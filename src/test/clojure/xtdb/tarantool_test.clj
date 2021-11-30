(ns xtdb.tarantool-test
  (:require
    [clojure.test :refer [deftest testing is]]
    [xtdb.tarantool :as sut]))


(deftest square-test
  (testing "dummy test"
    (is (= 4 (sut/square 2)))))
