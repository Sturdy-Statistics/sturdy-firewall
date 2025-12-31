(ns sturdy.malli-firewall.transform-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [malli.core :as m]
   [malli.transform :as mt]
   [sturdy.malli-firewall.transform :as st]))

(def TestSchema
  [:map {:closed true}
   [:username :string]
   [:token :string]])

(defn- test-smart-decode [schema data]
  (m/decode
   schema
   data
   (mt/transformer
    (st/smart-key-transformer {:strip-unknown-keys? true})
    mt/string-transformer)))

(deftest smart-key-transformer-test
  (testing "Exact matches are keywordized and kept"
    (is (= {:username "bob"}
           (test-smart-decode TestSchema {"username" "bob"}))))

  (testing "Small typos are keywordized (to allow Malli to report them)"
    ;; "usernam" is only 1 char away from "username"
    (let [res (test-smart-decode TestSchema {"usernam" "bob"})]
      (is (contains? res "usernam") "Should retain 'usernam' because it is a near-miss")
      (is (string? (first (keys res))))))

  (testing "Garbage keys are NOT keywordized and are stripped"
    (let [garbage "a_very_long_random_string_xyz_123"
          res (test-smart-decode TestSchema {garbage "data" "username" "bob"})]
      (is (= {:username "bob"} res) "Garbage should be gone")
      (is (nil? (find-keyword garbage)) "Garbage MUST NOT be interned in memory")))

  (testing "Case sensitivity is respected (or handled by spell-check)"
    ;; "USERNAME" is often considered a typo/near-miss by spell checkers
    (let [res (test-smart-decode TestSchema {"USERNAME" "bob"})]
      (is (contains? res "USERNAME") "Should likely intern uppercase variants for error reporting")))

  (testing "Multiple typos still result in stripping if too far apart"
    ;; "zx81_computer" is nowhere near "username" or "token"
    (let [weird-key "zx81_computer"
          res (test-smart-decode TestSchema {weird-key "vintage"})]
      (is (empty? res))
      (is (nil? (find-keyword weird-key))))))

(deftest transformer-memory-safety-test
  (let [schema [:map [:a :int]]
        ;; Generate a unique string for this specific test run
        junk-key (str "junk-" (random-uuid))]

    (testing "The Smart Transformer should NOT intern unknown strings even with :strip-unknown-keys? false"
      (m/decode schema {junk-key "val"} (st/smart-key-transformer {:strip-unknown-keys? false}))
      (is (nil? (find-keyword junk-key))
          "Smart transformer interned a garbage key!"))))

(def TestSchema2
  [:map {:closed true}
   [:username :string]
   [:user/id :int]
   [:active? {:optional true} :boolean]])

(deftest smart-key-transformer-extended-test
  (testing "strip-unknown-keys? false preserves garbage as strings"
    (let [junk-key (str "junk-" (random-uuid))
          input {junk-key "data" "username" "bob"}
          ;; Use transformer directly to see raw output before validation
          res (m/decode TestSchema input
                        (st/smart-key-transformer {:strip-unknown-keys? false}))]
      (is (= "data" (get res junk-key)) "Should keep the junk key as a string")
      (is (keyword? (find-keyword "username")) "Username is safe to intern")
      (is (nil? (find-keyword junk-key)) "Junk key must NOT be interned")))

  (testing "strip-unknown-keys? false converts existing keywords to strings if not in schema"
    (let [input {:unknown-kw "val" :username "bob"}
          res (m/decode TestSchema input
                        (st/smart-key-transformer {:strip-unknown-keys? false}))]
      (is (contains? res "unknown-kw") "Should be converted to string key")
      (is (not (contains? res :unknown-kw)) "Original keyword key should be replaced")
      (is (keyword? (first (keys (select-keys res [:username])))) "Valid key stays a keyword")))

  (testing "Namespaced keys and optional keys"
    (let [input {"username" "alice"
                 "user/id" "123"
                 "active?" "true"}
          ;; Using the full pipeline including string-to-type coercion
          transformer (mt/transformer
                       (st/smart-key-transformer {:strip-unknown-keys? true})
                       mt/string-transformer)
          res (m/decode TestSchema2 input transformer)]
      (is (= {:username "alice" :user/id 123 :active? true} res)
          "Should correctly handle namespaced strings and boolean coercion"))))
