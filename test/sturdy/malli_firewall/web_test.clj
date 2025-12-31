(ns sturdy.malli-firewall.web-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [sturdy.malli-firewall.web :as web]
   [sturdy.malli-firewall.schemas :as schemas]))

(deftest with-schema-test
  (testing "Successful validation binds coerced params and executes body"
    (let [request {:params {"username" "bob" "token" "secret"}}
          result (web/with-schema schemas/LoginRequest request
                   ;; We return the params to verify they were coerced to keywords
                   (:params request))]
      (is (= {:username "bob", :token "secret"} result)
          "The body should execute and see keywordized params.")))

  (testing "Validation failure short-circuits and passes structured details to the handler"
    (let [body-executed? (atom false)
          ;; Missing 'token'
          request {:params {"username" "bob"} :request-id "123"}]

      ;; Bind the dynamic var to a mock function for this test
      (binding [web/*bad-request-handler* (fn [_req details] details)]
        (let [result (web/with-schema schemas/LoginRequest request
                       (reset! body-executed? true)
                       :should-not-reach-here)]

          (is (false? @body-executed?)
              "The body should NOT be evaluated if validation fails.")

          (is (= "Invalid request parameters" (:message result))
              "The macro should pass the standard error message.")

          (is (get-in result [:problems :token])
              "The macro should pass the structured problems map to the handler.")))))

  (testing "Security: Extra keys are stripped before the body sees them"
    (let [request {:params {"username" "alice"
                            "token" "pass"
                            "malicious_intern_attempt" "value"}}
          result (web/with-schema schemas/LoginRequest request
                   (:params request))]
      (is (not (contains? result :malicious_intern_attempt)))))

  ;; note the extra key here must not appear as a keyword anywhere!
  ;; (eg :malicious_intern_attempt in the test above)
  (testing "Security: Extra keys are not interned"
    (let [request {:params {"username" "alice"
                            "token" "pass"
                            "malicious_intern_attempt_2" "value"}}
          _result (web/with-schema schemas/LoginRequest request
                    (:params request))]
      (is (nil? (find-keyword "malicious_intern_attempt_2")))))

  (testing "UX: Typo keys are keywordized to allow error reporting"
    (binding [web/*bad-request-handler* (fn [_req details] details)]
     (let [request {:params {"usernam" "alice" "token" "pass"}}
           response (web/with-schema schemas/LoginRequest request
                      :should-not-reach-here)]
       (is (contains? (get-in response [:problems]) "usernam")
           "The error response should specifically mention the typoed key.")))))

(testing "Behavior when *strip-unknown-keys* is false"
  (binding [web/*strip-unknown-keys* false
            web/*bad-request-handler* (fn [_req details] details)]
    (let [junk-key (str "web-junk-" (random-uuid))
          request {:params {junk-key "val" "username" "bob" "token" "pass"}}
          response (web/with-schema schemas/LoginRequest request
                     :should-not-reach-here)]

      (is (contains? (:problems response) junk-key)
          "The unknown key should be present in the error map as a string")

      (is (= ["disallowed key"] (get-in response [:problems junk-key]))
          "The closed schema should fail validation because of the string key")

      (is (nil? (find-keyword junk-key))
          "Even when not stripping, the unknown key must NOT be interned"))))
