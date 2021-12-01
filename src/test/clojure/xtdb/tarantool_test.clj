(ns xtdb.tarantool-test
  (:require
    [clojure.test :refer [deftest testing is]]
    [xtdb.system :as system]
    [xtdb.tarantool :as sut])
  (:import
    (java.util.function
      Function
      UnaryOperator)))


;;
;; Helper functions
;;

(defn prep-module
  [overrides]
  (try
    (-> {:tarantool-client (merge {:xtdb/module 'xtdb.tarantool/->tnt-client} overrides)}
        (system/prep-system)
        :tarantool-client)
    (catch xtdb.IllegalArgumentException e
      (ex-message (ex-cause e)))))


(defn includes?
  [pattern x]
  (try
    (boolean (re-find pattern x))
    (catch Exception _
      false)))



;;
;; Tests
;;

(deftest default-exception-handler-test
  (testing "should be return an instance of `java.util.function.Function`"
    (is (instance? Function (sut/default-exception-handler)))))



(deftest default-request-retry-policy-test
  (testing "should be return an instance of `java.util.function.UnaryOperator`"
    (is (instance? UnaryOperator (sut/default-request-retry-policy {:delay 300})))))



(deftest ->tnt-client-test
  (testing "should be failed by specs"

    (is (includes? #"Arg :username required"
                   (prep-module {})))

    (is (includes? #"Arg :username = 123[\s\S]+:xtdb\.tarantool\/username"
                   (prep-module {:username 123})))

    (is (includes? #"Arg :password required"
                   (prep-module {:username "root"})))

    (is (includes? #"Arg :password = 123[\s\S]+:xtdb\.tarantool\/password"
                   (prep-module {:username "root"
                                 :password 123})))

    (is (includes? #"Arg :host = 123[\s\S]+:xtdb\.tarantool\/host"
                   (prep-module {:username "root"
                                 :password "root"
                                 :host     123})))

    (is (includes? #"Arg :port = -123[\s\S]+:xtdb\.tarantool\/port"
                   (prep-module {:username "root"
                                 :password "root"
                                 :host     "127.0.0.1"
                                 :port     -123})))

    (is (includes? #"Arg :exception-handler = 123[\s\S]+:xtdb\.tarantool\/exception-handler"
                   (prep-module {:username          "root"
                                 :password          "root"
                                 :exception-handler 123})))

    (is (includes? #"Arg :request-retry-policy = 123[\s\S]+:xtdb\.tarantool\/request-retry-policy"
                   (prep-module {:username             "root"
                                 :password             "root"
                                 :exception-handler    (sut/default-exception-handler)
                                 :request-retry-policy 123})))

    (is (includes? #"Arg :retries = -123[\s\S]+:xtdb\.tarantool\/retries"
                   (prep-module {:username             "root"
                                 :password             "root"
                                 :exception            (sut/default-exception-handler)
                                 :request-retry-policy (sut/default-request-retry-policy {:delay 500})
                                 :retries              -123}))))


  (testing "should be passed by specs"
    (is (nil? (prep-module {:username "root" :password "root"})))

    (is (nil? (prep-module {:username "root" :password "root" :exception-handler (sut/default-exception-handler)})))

    (is (nil? (prep-module {:username             "root"
                            :password             "root"
                            :exception-handler    (sut/default-exception-handler)
                            :request-retry-policy (sut/default-request-retry-policy {:delay 500})})))

    (is (nil? (prep-module {:username             "root"
                            :password             "root"
                            :exception-handler    (sut/default-exception-handler)
                            :request-retry-policy (sut/default-request-retry-policy {:delay 500})
                            :retries              3})))))
