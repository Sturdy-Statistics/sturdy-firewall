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

;; Use a macro (not a function) so that `body` is not evaluated unless validation passes.
(defmacro with-schema
  "Coerces and validates request params against schema.

  On success: Updates :params in the request with coerced values
  and executes body.
  On failure: Short-circuits and returns a 400 Bad Request via
  bad-request-response.

  Uses the dynamic variables
    *bad-request-handler* to format 400 responses
    *strip-unknown-keys* to control behavior for unknown keys
  See the docstrings for these symbols for more information.

  Usage:
     (with-schema schema/LoginRequest request
       (handler request))"
  [schema request & body]
  `(let [req# ~request
         ;; Use the raw params from the request
         raw-params# (or (:params req#) {})
         res# (c/validate ~schema raw-params#
                          {:strip-unknown-keys? *strip-unknown-keys*})]
     (if-let [errors# (:error res#)]
       ;; Short-circuit: body is never evaluated
       (bad-request-response req# errors#)
       ;; Success: Shadow the request symbol with coerced data
       (let [~request (assoc req# :params (:ok res#))]
         ~@body))))
