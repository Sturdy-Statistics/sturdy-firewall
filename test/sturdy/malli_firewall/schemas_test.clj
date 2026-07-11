(ns sturdy.malli-firewall.schemas-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [malli.core :as m]
   [malli.transform :as mt]
   [sturdy.malli-firewall.schemas :as schemas])
  (:import
   [java.util UUID]))

(deftest ends-with-ext-test
  (let [schema (schemas/ends-with-ext ".csv")]
    (testing "Accepts strings ending with the requested extension"
      (is (m/validate schema "report.csv"))
      (is (m/validate schema ".csv")))

    (testing "Rejects strings with a different ending"
      (is (false? (m/validate schema "report.tsv")))
      (is (false? (m/validate schema "report.csv.bak"))))

    (testing "Rejects non-string values"
      (is (false? (m/validate schema nil)))
      (is (false? (m/validate schema 42))))))

(deftest non-blank-string-test
  (testing "Accepts non-empty strings"
    (is (m/validate schemas/NonBlankString "x"))
    (is (m/validate schemas/NonBlankString " ")))

  (testing "Rejects empty and non-string values"
    (is (false? (m/validate schemas/NonBlankString "")))
    (is (false? (m/validate schemas/NonBlankString nil)))
    (is (false? (m/validate schemas/NonBlankString 42)))))

(deftest trimmed-string-test
  (testing "Accepts strings without leading or trailing whitespace"
    (is (m/validate schemas/TrimmedString "hello"))
    (is (m/validate schemas/TrimmedString "")))

  (testing "Rejects strings with leading or trailing whitespace"
    (is (false? (m/validate schemas/TrimmedString " hello")))
    (is (false? (m/validate schemas/TrimmedString "hello ")))
    (is (false? (m/validate schemas/TrimmedString "\thello\n"))))

  (testing "Rejects non-string values"
    (is (false? (m/validate schemas/TrimmedString nil)))
    (is (false? (m/validate schemas/TrimmedString 42)))))

(deftest positive-int-test
  (testing "Accepts positive integers"
    (is (m/validate schemas/PositiveInt 1))
    (is (m/validate schemas/PositiveInt 42)))

  (testing "Rejects zero, negative, and non-integer values"
    (is (false? (m/validate schemas/PositiveInt 0)))
    (is (false? (m/validate schemas/PositiveInt -1)))
    (is (false? (m/validate schemas/PositiveInt "1")))
    (is (false? (m/validate schemas/PositiveInt nil)))))

(deftest empty-request-test
  (testing "Accepts an empty request map and optional anti-forgery token"
    (is (m/validate schemas/EmptyRequest {}))
    (is (m/validate schemas/EmptyRequest {:__anti-forgery-token "token"})))

  (testing "Rejects unknown keys and non-string anti-forgery token"
    (is (false? (m/validate schemas/EmptyRequest {:extra "value"})))
    (is (false? (m/validate schemas/EmptyRequest {:__anti-forgery-token 42})))))

(deftest relative-uri-test
  (testing "Accepts path-only relative URIs"
    (doseq [uri ["/"
                 "/dashboard"
                 "/dashboard?tab=settings"
                 "/dashboard#profile"
                 "/files/report%20draft.pdf"]]
      (is (m/validate schemas/RelativeURI uri))))

  (testing "Rejects non-relative or empty values"
    (doseq [uri [nil
                 ""
                 "dashboard"
                 "https://example.com/dashboard"
                 "//example.com/dashboard"]]
      (is (false? (m/validate schemas/RelativeURI uri)))))

  (testing "Rejects raw whitespace and control characters"
    (doseq [uri ["/with space"
                 "/with\ttab"
                 "/with\nnewline"
                 "/with\rreturn"
                 "/\r\nHeader: x"]]
      (is (false? (m/validate schemas/RelativeURI uri)))))

  (testing "Rejects percent-encoded control characters"
    (doseq [uri ["/%0d%0aHeader:x"
                 "/%0D%0AHeader:x"
                 "/path/%09tab"
                 "/path/%7Fdelete"]]
      (is (false? (m/validate schemas/RelativeURI uri)))))

  (testing "Rejects raw and percent-encoded backslashes"
    (doseq [uri ["/\\evil.example"
                 "/\\/evil.example"
                 "/path\\segment"
                 "/%5cevil.example"
                 "/%5Cevil.example"
                 "/%5c/evil.example"
                 "/path%5Csegment"]]
      (is (false? (m/validate schemas/RelativeURI uri))))))

(deftest tagged-uuid-test
  (let [Schema      (schemas/tagged-uuid "org")
        raw-uuid    "018f0000-0000-0000-0000-000000000000"
        uuid-obj    (UUID/fromString raw-uuid)
        valid-input (str "org-" raw-uuid)]

    (testing "Decoding: valid prefixed string becomes a java.util.UUID object"
      (is (= uuid-obj
             (m/decode Schema valid-input mt/string-transformer))))

    (testing "Decoding Failure: wrong prefix fails to decode and fails validation"
      (let [bad-input (str "ds-" raw-uuid)
            decoded   (m/decode Schema bad-input mt/string-transformer)]
        ;; The custom decoder should safely fall back to returning the raw string
        (is (= bad-input decoded))
        ;; And because it's a string, the underlying `uuid?` schema check will fail
        (is (false? (m/validate Schema decoded)))))

    (testing "Decoding Failure: missing prefix fails to decode"
      (let [decoded (m/decode Schema raw-uuid mt/string-transformer)]
        (is (= raw-uuid decoded))
        (is (false? (m/validate Schema decoded)))))

    (testing "Decoding Failure: valid prefix but garbage UUID fails to decode"
      (let [bad-input "org-not-a-real-uuid"
            decoded   (m/decode Schema bad-input mt/string-transformer)]
        (is (= bad-input decoded))
        (is (false? (m/validate Schema decoded)))))

    (testing "Encoding: UUID object becomes a prefixed string"
      (is (= valid-input
             (m/encode Schema uuid-obj mt/string-transformer))))

    (testing "Encoding: Ignores non-UUIDs (safely passes them through)"
      (is (= "just-a-string"
             (m/encode Schema "just-a-string" mt/string-transformer))))))
