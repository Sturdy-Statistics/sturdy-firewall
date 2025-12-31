(ns sturdy.malli-firewall.util-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [sturdy.malli-firewall.util :as util]))

(deftest full-name-test
  (testing "Standard string and keyword handling"
    (is (= "username" (util/full-name "username")) "Should return string as-is")
    (is (= "username" (util/full-name :username)) "Should return name of simple keyword"))

  (testing "Namespaced keywords"
    (is (= "user/id" (util/full-name :user/id))
        "Should return 'namespace/name' for namespaced keywords")
    (is (= "sturdy.malli-firewall/id" (util/full-name :sturdy.malli-firewall/id))
        "Should handle long/qualified namespaces"))

  (testing "Graceful failure for unsupported types"
    (is (nil? (util/full-name nil)) "Should return nil for nil input")
    (is (nil? (util/full-name 123)) "Should return nil for numbers")
    (is (nil? (util/full-name [])) "Should return nil for collections"))

  (testing "Potential edge cases"
    (is (= "" (util/full-name "")) "Should handle empty strings")
    (is (= " " (util/full-name " ")) "Should handle whitespace strings")))
