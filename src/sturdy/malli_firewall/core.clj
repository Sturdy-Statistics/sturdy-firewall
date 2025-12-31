(ns sturdy.malli-firewall.core
  (:require
   [malli.core :as m]
   [malli.error :as me]
   [malli.transform :as mt]
   [sturdy.malli-firewall.transform :as st]))

;;; ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Transformers

(def ^:private decode-transformer-default
  "Decodes common string values (\"42\"→42, \"true\"→true) and fills defaults.
  We intentionally DO NOT strip unknown keys here to validate closed
  schemas.

  Keys may be string or keyword.  Valid keys are always converted to
  keywords and unknown keys are always converted to strings."
  (mt/transformer
   (st/smart-key-transformer {:strip-unknown-keys? false})
   mt/string-transformer
   mt/default-value-transformer))

(def ^:private decode-transformer-open
  "Decodes common string values (\"42\"→42, \"true\"→true) and fills defaults.
  We DO strip unknown keys, so make sure the relevant maps are fully
  specified.

  Keys may be string or keyword.  Valid keys are always converted to
  keywords and unknown keys are stripped."
  (mt/transformer
   (st/smart-key-transformer {:strip-unknown-keys? true})
   mt/string-transformer
   mt/default-value-transformer))

;;; ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Core

(defn coerce
  "Transforms raw input (e.g., Ring params) into internal Clojure data.

  Converts string keys to keywords and coerces string values to their
  schema-defined types. By default, uses smart-key-transformer to
  silently drop unknown keys to prevent arbitrary keyword
  interning (DoS).  If `:strip-unknown-keys?` is falsey, unknown keys
  are kept in the map, but are left as strings.  This enables closed
  schemas to fail validation, while still avoiding interning arbitrary
  user input.

  Returns coerced data map (unvalidated)."
  [schema params & [{:keys [strip-unknown-keys?]
                     :or {strip-unknown-keys? true}}]]
  (let [decode-transformer (if strip-unknown-keys?
                             decode-transformer-open
                             decode-transformer-default)]
    (m/decode schema params decode-transformer)))


(defn validate
  "Coerces and validates input against a Malli schema.

  Returns a map with either:
  - :ok     The coerced data map.
  - :error  A map containing a human-readable :message and a
            :problems map (humanized Malli errors with spell-checking).

  See `coerce` for documentation on the options and coercion."
  [schema params & [opts]]
  (let [coerced (coerce schema params opts)]
    (if (m/validate schema coerced)
      {:ok coerced}
      (let [expl (->> coerced
                      (m/explain schema)
                      (me/with-spell-checking)
                      (me/humanize))]
        {:error {:message "Invalid request parameters"
                 :problems expl}}))))

(defn assert-valid!
  "Ensures input is valid or terminates execution.

  Returns coerced data on success.

  Throws an ex-info with type :bad-request on failure, containing
  humanized error details in the exception data.  Ideal for use in
  request-handling macros.

  See `coerce` for documentation on the options and coercion."
  [schema params & [opts]]
  (let [res (validate schema params opts)]
    (if (:error res)
      (throw (ex-info "Bad request" (:error res)))
      (:ok res))))
