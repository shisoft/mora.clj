(ns mora.main
  (:gen-class)
	(require [org.httpkit.client :as http]
					 [monger.core :as mg]
					 [monger.collection :as mc]
					 [monger.query :as mq]
					 [monger.operators :refer :all]
					 [net.cgrand.enlive-html :as html]
					 [digest]
					 [clojure.pprint])
	(:import org.bson.types.ObjectId
					 java.io.StringReader))


(defn -main
	[& args]
	(let [conn (mg/connect {:host "127.0.0.1"})
				db (mg/get-db conn "web")
				coll "url"]
		;;	(println (mq/with-collection db coll (mq/find {}) (mq/sort (array-map :time -1))))
		(while true
			(let [{url :url} (mc/find-and-modify db coll {} {$set {:time (System/currentTimeMillis)}} {:sort (array-map :time 1, :_id 1)})]
				(println "Get body for " url)
				@(http/get url {:as :text}
									 (fn [{:keys [status body] :as a}]
										 (println "Got body for " url)
										 (let [parsed-html (html/html-resource (StringReader. body))]
											 (doseq [a-tag (html/select parsed-html [:a])]
												 (let [aurl (get-in a-tag [:attrs :href])]
													 (mc/insert db coll {:url aurl, :time (System/currentTimeMillis)})
													 (mc/update db "page" {:uhash (digest/md5 aurl)} {$set {:html body, :url aurl, :time (System/currentTimeMillis)}} {:upsert true})))
											 ))
									 )))))

