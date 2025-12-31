(ns sturdy.malli-firewall.core-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [sturdy.malli-firewall.core :as sc]
   [sturdy.malli-firewall.schemas :as ss]))

(def TestRequest
  [:map {:closed true}
   [:username  ss/NonBlankString]
   [:token     ss/NonBlankString]

   [:next  {:optional true} ss/RelativeURI]
   [:error {:optional true} any?]

   [:__anti-forgery-token {:optional true} string?]])

(deftest validate-test
  (testing "Successful validation with mixed key types"
    (let [input {"username" "alice" :token "secret123" "next" "/dashboard"}]
      (is (contains? (sc/validate TestRequest input) :ok))))

  (testing "Strip extra keys (Security Check)"
    (let [input {:username "bob" :token "pass" :malicious "payload"}
          res (sc/validate TestRequest input {:strip-unknown-keys? true})]
      (is (nil? (:error res)))
      (is (not (contains? (:ok res) :malicious)))))

  (testing "Fail on extra keys (Security Check)"
    (let [input {:username "bob" :token "pass" :malicious "payload"}
          {:keys [error]} (sc/validate TestRequest input {:strip-unknown-keys? false})]
      (is (= "Invalid request parameters"
             (:message error)))
      (is (= {"malicious" ["disallowed key"]}
             (:problems error)))))

  (testing "Fail on missing keys"
    (let [input {:username "bob"}
          {:keys [error]} (sc/validate TestRequest input {:strip-unknown-keys? false})]
      (is (= "Invalid request parameters"
             (:message error)))
      (is (= {:token ["missing required key"]}
             (:problems error)))))

  (testing "Spell checking on string-keyed typos (stripped)"
    (let [input {"usernam" "bob" "token" "pass"}
          {:keys [error]} (sc/validate TestRequest input {:strip-unknown-keys? true})]
      (is (= {"usernam" ["should be spelled :username"]}
             (:problems error)))))

  (testing "Spell checking on string-keyed typos (unstripped)"
    (let [input {"usernam" "bob" "token" "pass"}
          {:keys [error]} (sc/validate TestRequest input {:strip-unknown-keys? false})]
      (is (= {"usernam" ["should be spelled :username"]}
             (:problems error)))))

  (testing "Namespaced key handling (mixed input)"
    (let [schema [:map [:user/id :int]]
          ;; String input "user/id" should match keyword :user/id
          input {"user/id" "123"}
          res (sc/validate schema input)]
      (is (= {:ok {:user/id 123}} res))))

  (testing "Nested map validation and stripping"
    (let [schema [:map [:user [:map [:id :int]]]]
          input {"user" {"id" "123" "extra" "garbage"}}
          res (sc/coerce schema input {:strip-unknown-keys? true})]
      (is (= {:user {:id 123}} res) "Should strip nested extra keys")))

  (testing "Graceful handling of non-map input"
    (is (nil? (sc/coerce [:map [:a :int]] nil)) "Nil input should return nil")
    (is (not (contains? (sc/validate [:map [:a :int]] []) :ok)) "Vector input should fail validation"))

  (testing "Relative URI validation"
    (is (contains? (sc/validate TestRequest {:username "a" :token "b" :next "/ok"}) :ok))
    (let [{:keys [error]} (sc/validate TestRequest {:username "a" :token "b" :next "http://bad"})]
      (is (= {:next ["must begin with '/'."]} (:problems error))))))

(deftest assert-valid!-test
  (testing "Throws on invalid data"
    (is (thrown-with-msg? clojure.lang.ExceptionInfo #"Bad request"
                          (sc/assert-valid! TestRequest {"username" "bob"})))) ; missing token

  (testing "Returns data on success"
    (is (= {:username "bob" :token "pass"}
           (select-keys (sc/assert-valid! TestRequest {:username "bob" :token "pass"})
                        [:username :token])))))

(deftest core-coerce-memory-safety-test
  (let [schema [:map [:a :int]]
        junk-key (str "junk-" (java.util.UUID/randomUUID))]

    (testing "Coerce with strip-unknown-keys? true should be safe"
      (sc/coerce schema {junk-key "val"} {:strip-unknown-keys? true})
      (is (nil? (find-keyword junk-key))
          "Coerce interned garbage even with stripping enabled."))

    (testing "Coerce with strip-unknown-keys? false is also safe"
      (let [junk-key-2 (str "junk-" (java.util.UUID/randomUUID))]
        (sc/coerce schema {junk-key-2 "val"} {:strip-unknown-keys? false})
        (is (nil? (find-keyword junk-key-2))
            "Coerce interned garbage with stripping disabled (should be left as string).")))))
