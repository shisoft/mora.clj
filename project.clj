(defproject mora.clj "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [
									[org.clojure/clojure "1.6.0"],
									[http-kit "2.1.18"],
									[com.novemberain/monger "2.0.0"],
									[clojure-hbase "0.92.4"],
									[enlive "1.1.5"],
									[digest "1.4.4"]
								]
  :main ^:skip-aot mora.main
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
