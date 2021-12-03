(ns xtdb.tarantool-test
  (:require
    [clojure.test :refer [deftest testing is]]
    [xtdb.api :as xt]
    [xtdb.system :as system]
    [xtdb.tarantool :as sut]
    [xtdb.tx.event :as xte])
  (:import
    (io.tarantool.driver.core
      RetryingTarantoolTupleClient)
    (java.util
      ArrayList)
    (java.util.concurrent
      ExecutionException)
    (java.util.function
      Function
      UnaryOperator)))


;;
;; Helper functions
;;

(defn prep-tnt-client
  [overrides]
  (try
    (-> {:tnt-client (merge {:xtdb/module 'xtdb.tarantool/->tnt-client} overrides)}
        (system/prep-system)
        :tnt-client)
    (catch xtdb.IllegalArgumentException e
      (ex-message (ex-cause e)))))


(defn start-tnt-client
  [overrides]
  (-> {:tnt-client (merge {:xtdb/module 'xtdb.tarantool/->tnt-client} overrides)}
      (system/prep-system)
      (system/start-system)
      :tnt-client))


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



(deftest to-inst-test
  (testing "should be returned an instance of `java.util.Date`"
    (is (= #inst"2021-12-03T01:26:09.506-00:00" (sut/to-inst 1638494769506799)))))



(deftest prepare-fn-args-test
  (testing "should be returned an instance of `java.util.ArrayList`"
    (doseq [x [nil 1 1/2 "string" \c :keyword ::qualified-keyword 'symbol 'qualified-symbol '(1 2 3) [1 2 3] #{1 2 3} (ArrayList. [1 2 3])]]
      (is (instance? ArrayList (sut/prepare-fn-args x))))))



(deftest ^:unit ->tnt-client-specs-test
  (testing "should be failed by specs"

    (is (includes? #"Arg :username required"
                   (prep-tnt-client {})))

    (is (includes? #"Arg :username = 123[\s\S]+:xtdb\.tarantool\/username"
                   (prep-tnt-client {:username 123})))

    (is (includes? #"Arg :password required"
                   (prep-tnt-client {:username "root"})))

    (is (includes? #"Arg :password = 123[\s\S]+:xtdb\.tarantool\/password"
                   (prep-tnt-client {:username "root"
                                     :password 123})))

    (is (includes? #"Arg :host = 123[\s\S]+:xtdb\.tarantool\/host"
                   (prep-tnt-client {:username "root"
                                     :password "root"
                                     :host     123})))

    (is (includes? #"Arg :port = -123[\s\S]+:xtdb\.tarantool\/port"
                   (prep-tnt-client {:username "root"
                                     :password "root"
                                     :host     "127.0.0.1"
                                     :port     -123})))

    (is (includes? #"Arg :exception-handler = 123[\s\S]+:xtdb\.tarantool\/exception-handler"
                   (prep-tnt-client {:username          "root"
                                     :password          "root"
                                     :exception-handler 123})))

    (is (includes? #"Arg :request-retry-policy = 123[\s\S]+:xtdb\.tarantool\/request-retry-policy"
                   (prep-tnt-client {:username             "root"
                                     :password             "root"
                                     :exception-handler    (sut/default-exception-handler)
                                     :request-retry-policy 123})))

    (is (includes? #"Arg :retries = -123[\s\S]+:xtdb\.tarantool\/retries"
                   (prep-tnt-client {:username             "root"
                                     :password             "root"
                                     :exception            (sut/default-exception-handler)
                                     :request-retry-policy (sut/default-request-retry-policy {:delay 500})
                                     :retries              -123}))))


  (testing "should be passed by specs"
    (is (nil? (prep-tnt-client {:username "root" :password "root"})))

    (is (nil? (prep-tnt-client {:username "root" :password "root" :exception-handler (sut/default-exception-handler)})))

    (is (nil? (prep-tnt-client {:username             "root"
                                :password             "root"
                                :exception-handler    (sut/default-exception-handler)
                                :request-retry-policy (sut/default-request-retry-policy {:delay 500})})))

    (is (nil? (prep-tnt-client {:username             "root"
                                :password             "root"
                                :exception-handler    (sut/default-exception-handler)
                                :request-retry-policy (sut/default-request-retry-policy {:delay 500})
                                :retries              3})))))



(deftest ^:integration ->tnt-client-test
  (testing "should be throw an exception with a bad connection options"
    (let [tnt-client (start-tnt-client {:host     "localhost"
                                        :port     3302
                                        :username "root"
                                        :password "root"})]
      (is (instance? RetryingTarantoolTupleClient tnt-client))
      (is (thrown-with-msg? ExecutionException #"The client is not connected to Tarantool server"
            (sut/get-box-info tnt-client sut/default-complex-types-mapper)))))


  (testing "should be returned a successful response from the tarantool"
    (let [tnt-client (start-tnt-client {:username "root", :password "root"})
          box-info   (sut/get-box-info tnt-client sut/default-complex-types-mapper)]
      (is (instance? RetryingTarantoolTupleClient tnt-client))
      (is (= "0.0.0.0:3301" (get box-info "listen")))
      (is (includes? #"2.8.2" (get box-info "version")))
      (sut/close tnt-client))))



(deftest ^:integration ->tx-log-test
  (let [tnt-client (start-tnt-client {:username "root", :password "root"})
        mapper     sut/default-complex-types-mapper
        tx-log     {:tnt-client tnt-client, :mapper mapper}
        truncate   #(sut/execute tnt-client mapper "xtdb.db.truncate")]
    (testing "should be returned the same tx-id"
      (truncate)
      (is (nil? (sut/latest-submitted-tx tx-log)))
      (let [tx        @(sut/submit-tx tx-log [[::xt/put {:xt/id "hi2u", :user/name "zig"}]])
            latest-tx (sut/latest-submitted-tx tx-log)
            txs       (sut/open-tx-log* tx-log nil)]
        (is (every? seq [tx latest-tx txs]))
        (is (= #{::xt/tx-id} (->> latest-tx (keys) (set))))
        (is (= #{::xt/tx-id ::xt/tx-time} (->> tx (keys) (set))))
        (is (= #{::xt/tx-id ::xt/tx-time ::xte/tx-events} (->> txs (map keys) (flatten) (set))))
        (is (= (::xt/tx-id tx) (::xt/tx-id latest-tx) (::xt/tx-id (last txs)))))
      (truncate)
      (Thread/sleep 5000)
      (sut/close tnt-client))))



(comment

  (def node
    (xt/start-node
      {::tnt-client      {:xtdb/module `sut/->tnt-client
                          :username    "root"
                          :password    "root"}

       :xtdb/index-store {:xtdb/module 'xtdb.kv.index-store/->kv-index-store}
       :xtdb/tx-log      {:xtdb/module `sut/->tx-log
                          :tnt-client  ::tnt-client}}))

  (sut/close node)


  (xt/submit-tx node [[::xt/put {:xt/id "hi2u", :user/name "zig"}]])
  ;; => #:xtdb.api{:tx-id 4, :tx-time #inst"2021-12-03T22:00:34.561-00:00"}


  (xt/q (xt/db node) '{:find  [e]
                       :where [[e :user/name "zig"]]})
  ;; => #{["hi2u"]}


  (xt/q (xt/db node)
        '{:find  [(pull ?e [*])]
          :where [[?e :xt/id "hi2u"]]})
  ;; => #{[{:user/name "zig", :xt/id "hi2u"}]}



  (def history (xt/entity-history (xt/db node) "hi2u" :desc {:with-docs? true}))
  ;;=> [#:xtdb.api{:tx-time #inst"2021-12-03T22:00:34.561-00:00",
  ;;               :tx-id 4,
  ;;               :valid-time #inst"2021-12-03T22:00:34.561-00:00",
  ;;               :content-hash #xtdb/id"32f90c7020097d1232d14a9af395fc6aabd02ad7",
  ;;               :doc {:user/name "zig", :xt/id "hi2u"}}]


  (->> (map ::xt/doc history)
       (filter #(= (get % :user/name) "zig")))
  ;; => ({:user/name "zig", :xt/id "hi2u"})

  )
