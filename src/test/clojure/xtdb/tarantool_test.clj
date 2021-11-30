(ns xtdb.tarantool-test
  (:require
    [clojure.test :refer [deftest testing is]]
    [xtdb.tarantool :as sut])
  (:import
    (java.util.function
      Function
      UnaryOperator)))


(deftest default-exception-handler-test
  (testing "should be return an instance of java.util.function.Function"
    (is (instance? Function (sut/default-exception-handler)))))


(deftest default-request-retry-policy-test
  (testing "should be return an instance of java.util.function.UnaryOperator"
    (is (instance? UnaryOperator (sut/default-request-retry-policy {:delay 300})))))
