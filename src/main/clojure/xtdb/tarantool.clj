(ns xtdb.tarantool
  (:require
    [clojure.spec.alpha :as s]
    [clojure.string :as str]
    [xtdb.system :as system])
  (:import
    (io.tarantool.driver.api
      TarantoolClient
      TarantoolClientFactory)
    (io.tarantool.driver.api.retry
      TarantoolRequestRetryPolicies$AttemptsBoundRetryPolicyFactory$Builder)
    (io.tarantool.driver.mappers
      DefaultMessagePackMapperFactory)
    (java.util.function
      Function
      UnaryOperator)))


(set! *warn-on-reflection* true)


;;
;; Default mappers and builders
;;

(def ^{:doc "Modification-safe instance of the messagepack mapper.
  The instance contains converters for simple types and complex types java.util.Map and java.util.List."}
  default-complex-types-mapper
  (.defaultComplexTypesMapper (DefaultMessagePackMapperFactory/getInstance)))


(def ^{:doc "Modification-safe instance of the messagepack mapper.
  The instance already contains converters for simple types."}
  default-simple-type-mapper
  (.defaultSimpleTypeMapper (DefaultMessagePackMapperFactory/getInstance)))


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



;; Specs

(s/def ::host ::system/string)
(s/def ::port ::system/pos-int)
(s/def ::username ::system/string)
(s/def ::password ::system/string)
(s/def ::retries ::system/pos-int)
(s/def ::exception-handler #(instance? Function %))
(s/def ::request-retry-policy #(instance? UnaryOperator %))


;; Tarantool client

(defn ^TarantoolClient ->tnt-client
  "Returns a tarantool client as a component of the xtdb.system."
  {::system/args {:host                 {:doc       "Host"
                                         :spec      ::host
                                         :required? true
                                         :default   "localhost"}
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
                  :retries              {:doc       "Retries"
                                         :spec      ::retries
                                         :default   3
                                         :required? true}
                  :exception-handler    {:doc       "Exception handler"
                                         :spec      ::exception-handler
                                         :default   (default-exception-handler)
                                         :required? true}
                  :request-retry-policy {:doc       "Request retry policy"
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
