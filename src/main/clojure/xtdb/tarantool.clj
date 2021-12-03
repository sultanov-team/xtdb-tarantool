(ns xtdb.tarantool
  (:require
    [clojure.spec.alpha :as s]
    [clojure.string :as str]
    [juxt.clojars-mirrors.nippy.v3v1v1.taoensso.nippy :as nippy]
    [xtdb.api :as xt]
    [xtdb.db :as db]
    [xtdb.io :as xio]
    [xtdb.system :as system]
    [xtdb.tx.event :as xte]
    [xtdb.tx.subscribe :as tx-sub])
  (:import
    (clojure.lang
      PersistentHashSet
      PersistentList
      PersistentTreeSet
      PersistentVector)
    (io.tarantool.driver.api
      TarantoolClient
      TarantoolClientFactory)
    (io.tarantool.driver.api.retry
      TarantoolRequestRetryPolicies$AttemptsBoundRetryPolicyFactory$Builder)
    (io.tarantool.driver.mappers
      DefaultMessagePackMapper
      DefaultMessagePackMapperFactory)
    (java.io
      Closeable)
    (java.lang
      AutoCloseable)
    (java.time
      Duration
      Instant)
    (java.time.temporal
      ChronoUnit)
    (java.util
      ArrayList
      Date
      List)
    (java.util.concurrent
      CompletableFuture)
    (java.util.function
      Function
      UnaryOperator)))


(set! *warn-on-reflection* true)


;;
;; Helper functions
;;

(defn to-inst
  "Converts microseconds to `java.util.Date`."
  [^long ts]
  (-> (Instant/EPOCH)
      (.plus ts ChronoUnit/MICROS)
      (Date/from)))


(defprotocol ITarantoolCallFnArgument
  "This protocol is used to simplify manipulations with the arguments of the function defined in the Tarantool instance."
  :extend-via-metadata true
  (-prepare-fn-args [argument]))


(extend-protocol ITarantoolCallFnArgument
  PersistentList
  (-prepare-fn-args [coll] (ArrayList. coll))

  PersistentVector
  (-prepare-fn-args [coll] (ArrayList. coll))

  PersistentHashSet
  (-prepare-fn-args [coll] (ArrayList. coll))

  PersistentTreeSet
  (-prepare-fn-args [coll] (ArrayList. coll))

  Object
  (-prepare-fn-args [object] (-prepare-fn-args [object]))

  nil
  (-prepare-fn-args [_] (ArrayList.)))


(defn prepare-fn-args
  "Prepares the list of arguments for executing the function defined in the Tarantool instance."
  (^List [] (-prepare-fn-args nil))
  (^List [x] (-prepare-fn-args x)))


(defn serialize
  [x]
  (nippy/freeze-to-string x))


(defn deserialize
  [x]
  (nippy/thaw-from-string x))



;;
;; Default mappers
;;

(def ^{:doc "Modification-safe instance of the messagepack mapper.
  The instance contains converters for simple types and complex types `java.util.Map` and `java.util.List`."}
  default-complex-types-mapper
  (.defaultComplexTypesMapper (DefaultMessagePackMapperFactory/getInstance)))


(def ^{:doc "Modification-safe instance of the messagepack mapper.
  The instance already contains converters for simple types."}
  default-simple-type-mapper
  (.defaultSimpleTypeMapper (DefaultMessagePackMapperFactory/getInstance)))



;;
;; Default builders
;;

(defn default-exception-handler
  "Returns a default exception handler."
  []
  (reify
    Function
    (apply [_ e]
      (str/includes? (.getMessage ^Exception e) "Unsuccessful attempt"))))


(defn default-request-retry-policy
  "Returns a default request retry policy."
  [{:keys [^long delay]
    :or   {delay 300}}]
  (reify
    UnaryOperator
    (apply [_ policy]
      (.withDelay ^TarantoolRequestRetryPolicies$AttemptsBoundRetryPolicyFactory$Builder policy delay))))



;;
;; Tarantool API
;;

(defn close
  "Closes the component."
  [^AutoCloseable x]
  (.close x))


(defn execute
  "Execute a function defined on the Tarantool instance.
  Params:
    * tnt-client - instance of `io.tarantool.driver.api.TarantoolClient`
    * mapper     - mapper for arguments object-to-MessagePack entity conversion and result values conversion
    * fn-name    - function name, must not be null or empty
    * fn-args    - function arguments (optional)"
  ([^TarantoolClient tnt-client ^DefaultMessagePackMapper mapper ^String fn-name]
   (execute tnt-client mapper fn-name nil))
  ([^TarantoolClient tnt-client ^DefaultMessagePackMapper mapper ^String fn-name fn-args]
   (-> ^CompletableFuture (.call tnt-client fn-name (prepare-fn-args fn-args) mapper)
       (.get)
       (first))))


(defn get-box-info
  [^TarantoolClient tnt-client ^DefaultMessagePackMapper mapper]
  (execute tnt-client mapper "box.info"))



;;
;; Tarantool client
;;

;; Specs

(s/def ::host ::system/string)
(s/def ::port ::system/pos-int)
(s/def ::username ::system/string)
(s/def ::password ::system/string)
(s/def ::retries ::system/pos-int)
(s/def ::exception-handler #(instance? Function %))
(s/def ::request-retry-policy #(instance? UnaryOperator %))


;; Component

(defn ^TarantoolClient ->tnt-client
  "Returns a tarantool client as a component of the xtdb.system."
  {::system/args {:host                 {:doc       "Host"
                                         :spec      ::host
                                         :default   "localhost"
                                         :required? true}
                  :port                 {:doc       "Port"
                                         :spec      ::port
                                         :default   3301
                                         :required? true}
                  :username             {:doc       "Username"
                                         :spec      ::username
                                         :required? true}
                  :password             {:doc       "Password"
                                         :spec      ::password
                                         :required? true}
                  :retries              {:doc       "The number of retry attempts for each request"
                                         :spec      ::retries
                                         :default   3
                                         :required? true}
                  :exception-handler    {:doc       "Function checking whether the given exception may be retried"
                                         :spec      ::exception-handler
                                         :default   (default-exception-handler)
                                         :required? true}
                  :request-retry-policy {:doc       "Retry policy that performs unbounded number of attempts. If the exception check passes, the policy returns true"
                                         :spec      ::request-retry-policy
                                         :default   (default-request-retry-policy {:delay 300})
                                         :required? true}}}
  [{:keys [^String host ^long port ^String username ^String password ^long retries ^Function exception-handler ^UnaryOperator request-retry-policy]}]
  (let [tnt-client (-> (TarantoolClientFactory/createClient)
                       (.withAddress host port)
                       (.withCredentials username password)
                       (.build))]
    (-> (TarantoolClientFactory/configureClient tnt-client)
        (.withRetryingByNumberOfAttempts retries exception-handler request-retry-policy)
        (.build))))



;;
;; API tx-log
;;

(defn submit-tx
  "Submits a `tx` to the `tx-log` and returns an instance of `clojure.lang.Delay`."
  [{:keys [tnt-client mapper]} tx-events]
  (let [{:strs [status data]} (execute tnt-client mapper "xtdb.tx_log.submit_tx" (serialize tx-events))]
    (when (= 201 status)
      (let [{:strs [tx_id tx_time]} data]
        (delay {::xt/tx-id   tx_id
                ::xt/tx-time (to-inst tx_time)})))))


(defn open-tx-log*
  "Returns the `tx-log` as a lazy sequence."
  [{:as tx-log :keys [tnt-client mapper]} after-tx-id]
  (let [txs (lazy-seq
              (let [{:strs [status data]} (execute tnt-client mapper "xtdb.tx_log.open_tx_log" after-tx-id)]
                (when (= 200 status)
                  (let [txs        (map (fn [{:strs [tx_id tx_time tx_events]}]
                                          {::xt/tx-id      tx_id
                                           ::xt/tx-time    (to-inst tx_time)
                                           ::xte/tx-events (deserialize tx_events)})
                                        data)
                        last-tx-id (::xt/tx-id (last txs))]
                    (cons txs (open-tx-log* tx-log last-tx-id))))))]
    (mapcat identity txs)))


(defn open-tx-log
  "Returns the `tx-log` as an instance of `xtdb.api.Cursor`."
  [tx-log after-tx-id]
  (xio/->cursor (constantly false) (open-tx-log* tx-log after-tx-id)))


(defn subscribe
  "Subscribe to the `tx-log` (via long-polling) using a `poll-wait-duration`."
  [{:as tx-log :keys [poll-wait-duration]} after-tx-id f]
  (tx-sub/handle-polling-subscription tx-log after-tx-id {:poll-sleep-duration poll-wait-duration} f))


(defn latest-submitted-tx
  "Returns the latest submitted `tx-id` if the `tx-log` is not empty. Otherwise, returns `nil`."
  [{:keys [tnt-client mapper]}]
  (when-let [{:strs [status data]} (execute tnt-client mapper "xtdb.tx_log.latest_submitted_tx")]
    (when (= 200 status)
      (let [{:strs [tx_id]} data]
        {::xt/tx-id tx_id}))))


(defrecord TarantoolTxLog
  [^TarantoolClient tnt-client ^DefaultMessagePackMapper mapper ^Duration poll-wait-duration]
  db/TxLog
  (submit-tx [tx-log tx-events] (submit-tx tx-log tx-events))
  (open-tx-log [tx-log after-tx-id] (open-tx-log tx-log after-tx-id))
  (subscribe [tx-log after-tx-id f] (subscribe tx-log after-tx-id f))
  (latest-submitted-tx [tx-log] (latest-submitted-tx tx-log))

  Closeable
  (close [_] (close tnt-client)))



;; Specs

(s/def ::mapper #(instance? DefaultMessagePackMapper %))
(s/def ::duration ::system/duration)



;; Component

(defn ->tx-log
  {::system/args {:mapper             {:doc       "Default implementation of MessagePackObjectMapper and MessagePackValueMapper"
                                       :spec      ::mapper
                                       :default   default-complex-types-mapper
                                       :required? true}
                  :poll-wait-duration {:doc       "How long to wait when polling the Tarantool instance"
                                       :spec      ::duration
                                       :default   (Duration/ofSeconds 1)
                                       :required? true}}
   ::system/deps {:tnt-client `->tnt-client}}
  [opts]
  (map->TarantoolTxLog opts))
