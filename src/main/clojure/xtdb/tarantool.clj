(ns xtdb.tarantool
  (:require
    [clojure.string :as str])
  (:import
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
