(ns sturdy.malli-firewall.schemas-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [malli.core :as m]
   [malli.transform :as mt]
   [sturdy.malli-firewall.schemas :as schemas])
  (:import
   [java.util UUID]))

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
