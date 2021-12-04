(ns xtdb.tarantool.example
  (:require
    [clojure.tools.namespace.repl :as tools.repl]
    [integrant.core :as ig]
    [integrant.repl :as ig.repl]
    [integrant.repl.state :as ig.repl.state]
    [xtdb.api :as xt]
    [xtdb.tarantool :as tnt])
  (:import
    (java.io
      Closeable)
    (java.time
      Duration)))


(tools.repl/set-refresh-dirs "src/dev/clojure")


(defn config
  []
  {::xtdb-tnt {::tnt-client             {:xtdb/module 'xtdb.tarantool/->tnt-client
                                         :username    "root"
                                         :password    "root"}
               :xtdb/tx-log             {:xtdb/module        'xtdb.tarantool/->tx-log
                                         :tnt-client         ::tnt-client
                                         :poll-wait-duration (Duration/ofSeconds 5)}
               :xtdb.http-server/server {:read-only?   true
                                         :server-label "[xtdb-tarantool] Console Demo"}}})


(defn prep
  []
  (ig.repl/set-prep! config))


(defn go
  []
  (prep)
  (ig.repl/go))


(def halt ig.repl/halt)
(def reset-all ig.repl/reset-all)


(defn system
  []
  ig.repl.state/system)


(defmethod ig/init-key ::xtdb-tnt [_ config]
  (xt/start-node config))


(defmethod ig/halt-key! ::xtdb-tnt [_ ^Closeable node]
  (tnt/close node))


(comment

  (reset-all)
  (halt)
  (go)
  ;; open http://localhost:3000/


  (def node
    (::xtdb-tnt (system)))

  (xt/submit-tx node [[::xt/put {:xt/id "xtdb-tarantool", :user/email "ilshat@sultanov.team"}]])
  ;; => #:xtdb.api{:tx-id 1, :tx-time #inst"2021-12-04T01:27:15.641-00:00"}


  (xt/q (xt/db node) '{:find  [e]
                       :where [[e :user/email "ilshat@sultanov.team"]]})
  ;; => #{["xtdb-tarantool"]}


  (xt/q (xt/db node)
        '{:find  [(pull ?e [*])]
          :where [[?e :xt/id "xtdb-tarantool"]]})
  ;; => #{[{:user/email "ilshat@sultanov.team", :xt/id "xtdb-tarantool"}]}



  (def history (xt/entity-history (xt/db node) "xtdb-tarantool" :desc {:with-docs? true}))
  ;; => [#:xtdb.api{:tx-time #inst"2021-12-04T01:31:14.080-00:00",
  ;;               :tx-id 2,
  ;;               :valid-time #inst"2021-12-04T01:31:14.080-00:00",
  ;;               :content-hash #xtdb/id"d0eb040d39fbdaa8699d867bc9fb9aa244b8e154",
  ;;               :doc {:user/email "ilshat@sultanov.team", :xt/id "xtdb-tarantool"}}]


  (->> (map ::xt/doc history)
       (filterv (comp (partial = "ilshat@sultanov.team") :user/email)))
  ;; => [{:user/email "ilshat@sultanov.team", :xt/id "xtdb-tarantool"}]
  )
