(ns sturdy.malli-firewall.schemas
  (:require
   [clojure.string :as string]))

;;; ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Utilities

(defn- ends-with?
  "Returns a predicate (string? && ends-with ext)."
  [ext]
  (fn [s] (and (string? s) (string/ends-with? s ext))))

(defn ends-with-ext
  "Schema form for 'string ending with ext' with a clear error message."
  [ext]
  [:fn {:error/message (str "must end with " ext)}
   (ends-with? ext)])

(defn- starts-with?
  "Returns a predicate (string? && start-with pfx)"
  [pfx]
  (fn [s] (and (string? s) (string/starts-with? s pfx))))

(def NonBlankString
  [:string {:min 1}])

(def TrimmedString
  [:and string? [:fn {:error/message "leading/trailing spaces not allowed"}
                 #(= % (string/trim %))]])

(defn relative-uri?
  [s]
  (and
   (string? s)
   (seq s)
   (string/starts-with? s "/")
   (not (string/starts-with? s "//"))))

(def RelativeURI
  [:fn {:error/message "must be a relative URI starting with '/'"} relative-uri?])

(def PositiveInt
  [:and [:int] [:> 0]])

;;; ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Schemas

(def EmptyRequest
  [:map {:closed true}
   [:__anti-forgery-token {:optional true} string?]])

(def LoginRequest
  [:map {:closed true}
   [:username  NonBlankString]
   [:token     NonBlankString]

   [:next  {:optional true} RelativeURI]
   [:error {:optional true} any?]

   [:__anti-forgery-token {:optional true} string?]])
