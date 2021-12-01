(ns xtdb.tarantool-test
  (:require
    [clojure.test :refer [deftest testing is]]
    [xtdb.system :as system]
    [xtdb.tarantool :as sut])
  (:import
    (io.tarantool.driver.core
      RetryingTarantoolTupleClient)
    (java.util.concurrent
      CompletableFuture
      ExecutionException)
    (java.util.function
      Function
      UnaryOperator)))


;;
;; Helper functions
;;

(defn prep-module
  [overrides]
  (try
    (-> {:tnt-client (merge {:xtdb/module 'xtdb.tarantool/->tnt-client} overrides)}
        (system/prep-system)
        :tnt-client)
    (catch xtdb.IllegalArgumentException e
      (ex-message (ex-cause e)))))


(defn start-module
  [overrides]
  (-> {:tnt-client (merge {:xtdb/module 'xtdb.tarantool/->tnt-client} overrides)}
      (system/prep-system)
      (system/start-system)
      :tnt-client))


(defn get-box-info
  [tnt-client]
  (-> ^CompletableFuture (.call tnt-client "box.info" [] sut/default-complex-types-mapper)
      (.get)
      (first)))


(defn includes?
  [pattern x]
  (try
    (boolean (re-find pattern x))
    (catch Exception _
      false)))



;;
;; Tests
;;

(deftest ^:unit default-exception-handler-test
  (testing "should be returned an instance of `java.util.function.Function`"
    (is (instance? Function (sut/default-exception-handler)))))



(deftest ^:unit default-request-retry-policy-test
  (testing "should be returned an instance of `java.util.function.UnaryOperator`"
    (is (instance? UnaryOperator (sut/default-request-retry-policy {:delay 300})))))



(deftest ^:unit ->tnt-client-specs-test
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



(deftest ^:integration ->tnt-client-test
  (testing "should be throw an exception with a bad connection options"
    (let [tnt-client (start-module {:host     "localhost"
                                    :port     3302
                                    :username "root"
                                    :password "root"})]
      (is (instance? RetryingTarantoolTupleClient tnt-client))
      (is (thrown-with-msg? ExecutionException #"The client is not connected to Tarantool server"
            (get-box-info tnt-client)))))


  (testing "should be returned a successful response from tarantool"
    (let [tnt-client (start-module {:username "root"
                                    :password "root"})
          box-info   (get-box-info tnt-client)]
      (is (instance? RetryingTarantoolTupleClient tnt-client))
      (is (= "0.0.0.0:3301" (get box-info "listen")))
      (is (includes? #"2.8.2" (get box-info "version"))))))
