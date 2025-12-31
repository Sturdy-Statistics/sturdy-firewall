## malli-firewall

A high-performance, security-focused Clojure validation library built on [**Malli**](https://github.com/metosin/malli).
Designed for web applications that need to ingest untrusted Ring parameters safely, while also providing human-friendly error reporting.

`malli-firewall` provides a macro `with-schema` which wraps a Ring handler to provide schema coercion & validation.
It converts valid keys to keywords, so your handler code may use keyword idioms.
However, unlike the Ring middleware [`wrap-keyword-params`](https://ring-clojure.github.io/ring/ring.middleware.keyword-params.html#var-wrap-keyword-params), it will not intern unexpected keys or arbitrary user input.

Unexpected keys which are suspected typos of valid keys are left in the map as strings human-friendly error reporting.
By default, all other keys are stripped automatically so they are never visible to handler code.
Optionally, you may re-bind the dynamic var `malli-firewall.web/*strip-unknown-keys*` to `false`; in this case unexpected keys are retained as strings, so that closed schemas may fail validation.

The wrapped handler does not run unless schema validation passes, and will only run with a valid schema.
On validation failure, `with-schema` returns a 400 response.
This response is controlled by the dynamic var `malli-firewall.web/*bad-request-handler*`, which you may re-bind to your own 400 handler.

## Core Principles

* **Memory Safety:** Prevents Denial of Service (DoS) attacks by strictly controlling keyword interning.  Arbitrary user input is never interned.
* **Typo Tolerance:** Intelligently retains "near-miss" keys (typos) so that Malli can provide helpful spell-checking suggestions.
* **Structured Errors:** Returns humanized error maps ready for frontend consumption or structured logging.
* **Zero-Overhead Handlers:** Uses macros to ensure validation and coercion happen before your business logic executes.

## Usage

### 1. Define Schemas

Define your requirements using standard Malli syntax.

```clj
(def LoginRequest
  [:map {:closed true}
   [:username  NonBlankString]
   [:token     NonBlankString]

   [:next  {:optional true} RelativeURI]
   [:error {:optional true} any?]

   [:__anti-forgery-token string?]])
```

### 2. Validate in Handlers

Use the with-schema macro in your Ring handlers to guard your logic.

```clj
(defn handle-login [request]
  (with-schema schemas/LoginRequest request
    (let [{:keys [username token]} (:params request)]
      ;; If code reaches here, request params are guaranteed:
      ;; 1. Valid: Schema validation passed.
      ;; 2. Keywordized: Valid keys are keywords (safe for internal use).
      ;; 3. Sanitized: Extra "garbage" keys are stripped by default.
      ;; 4. Typed: Values (e.g., "123" -> 123) are coerced per schema.
      (auth/login! username token))))
```

### 3. Use Directly:

```clj
(require '[sturdy.malli-firewall.schemas :as s])
(require '[sturdy.malli-firewall.core :as f])

(let [params {:user-id "bob" :tokens "howdy"}
      {:keys [error]} (f/validate s/LoginRequest params)]
  error)
;; => {:message "Invalid request parameters",
;;     :problems
;;     {:username ["missing required key"],
;;      :tokens ["should be spelled :token"]}}
```

## Configuration

You can customize the firewall behavior using dynamic variables:

| Variable                | Default  | Description                                            |
|:------------------------|:---------|:-------------------------------------------------------|
| `*bad-request-handler*` | JSON 400 | A function `(fn [request details])` called on failure. |
| `*strip-unknown-keys*`  | `true`   | When `false`, unknown keys are kept as safe strings.   |

## The Transformation Pipeline

Sturdy Schema uses a multi-stage transformation process to ensure data integrity:

1. **Smart Keywordization:** Strings matching schema keys are converted to keywords.  Close typos are retained as strings.
2. **DoS Protection:** Unknown strings are dropped (stripped) before they can be interned.
3. **Value Coercion:** String values (e.g., "true" or "42") are coerced to their proper types.
4. **Default Injection:** Missing optional keys are filled with schema-defined defaults.

## Security Notes

### Keyword Interning

This library avoids the "Keyword Leak" common in many Clojure web apps.
By using the smart-key-transformer, the internal keyword function is only called on strings defined in your schemas.
Randomly generated keys from attackers stay as strings and are discarded, protecting the JVM's Metaspace.

## Testing

The library includes a dedicated test suite for memory safety.
You can verify that no interning occurs for garbage keys by running:

```bash
clj -X:test
```

## License

Apache License 2.0

Copyright © Sturdy Statistics

<!-- Local Variables: -->
<!-- fill-column: 10000 -->
<!-- End: -->
