(ns sturdy.malli-firewall.web
  (:require
   [clojure.string :as string]
   [sturdy.malli-firewall.core :as c]
   [sturdy.malli-firewall.util :refer [full-name]]))

(defn- format-problem
  [[ky msg]]
  (str (full-name ky) ": " (string/join "; " msg)))

(defn- format-problems
  [problems]
  (string/join ".  "
               (map format-problem problems)))

(defn format-schema-error
  "Format a structured error response as a simple string, for handlers
  which do not support structured logging."
  [details]
  (let [{:keys [message problems]} details]

    (cond
      ;; both message and problems
      (and message problems)
      (str message ": " (format-problems problems))

      ;; either message or problems, but not both
      (seq? problems) (format-problems problems)

      ;; either message or nil
      :else message)))

(def ^:dynamic *bad-request-handler*
  "Users can bind this to a custom function, e.g. a ring handler for
  400 error responses."
  (fn [_request details]
    (let [{:keys [message problems]} details
          flat-message (format-schema-error details)]
     {:status 400
      :headers {"Content-Type" "application/json"}
      :body (cond-> {:error "Bad Request"}
              message (assoc :message message)
              problems (assoc :details problems) ;; note key change! `details` is a better public-facing name
              flat-message (assoc :flat-message flat-message))})))

(def ^:dynamic *strip-unknown-keys*
  "If `*strip-unknown-keys*` is truthy (default), unknown keys are
  silently dropped.  This prevents interning or processing any
  unwanted input.

  If `*strip-unknown-keys*` is falsey, unknown keys are kept in the
  map, but are left as strings.  This enables closed schemas to fail
  validation, while still avoiding interning arbitrary user input."
  true)

(defn bad-request-response
  [request details]
  (*bad-request-handler* request details))

(defmacro with-schemas
  "Coerces and validates multiple request maps against their respective schemas.

  Accepts a map of request keys to schemas (e.g., {:params LoginRequest, :path-params UserPath}).

  On success: Merges coerced maps back into the request and executes body.
  On failure: Short-circuits on the first failure and returns a 400 Bad Request via
  bad-request-response. Includes an `:in` key in the error details indicating which
  request key failed validation.

  Uses the dynamic variables
    *bad-request-handler* to format 400 responses
    *strip-unknown-keys* to control behavior for unknown keys

  Usage:
      (with-schemas {:params      schema/LoginRequest
                     :path-params schema/UserPath}
        request
        (handler request))"
  [schema-map request & body]
  `(let [req# ~request
         schemas# ~schema-map
         ;; Run validation across all defined keys in the schema-map
         results# (reduce-kv
                   (fn [acc# req-key# schema#]
                     (let [raw-data# (get req# req-key# {})
                           res# (c/validate schema# raw-data#
                                            {:strip-unknown-keys? *strip-unknown-keys*})]
                       (if-let [err# (:error res#)]
                         ;; Short-circuit on first error, tagging where it happened
                         (reduced {:error (assoc err# :in req-key#)})
                         ;; Accumulate the successfully coerced maps
                         (assoc-in acc# [:ok req-key#] (:ok res#)))))
                   {:ok {}}
                   schemas#)]
     (if-let [errors# (:error results#)]
       ;; Short-circuit: body is never evaluated
       (bad-request-response req# errors#)
       ;; Success: Merge the newly coerced data maps back into the request map
       (let [~request (merge req# (:ok results#))]
         ~@body))))

(defmacro with-schema
  "Convenience wrapper for `with-schemas` when only validating `:params`.

  Usage:
      (with-schema schema/LoginRequest request
        (handler request))"
  [schema request & body]
  `(with-schemas {:params ~schema} ~request ~@body))
