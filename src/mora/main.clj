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
					 java.io.StringReader
					 de.l3s.boilerpipe.extractors.ArticleExtractor
					 org.jsoup.Jsoup
					 ))

(defn strip-html-tags
	"Function strips HTML tags from string."
	[s]
	(.text (org.jsoup.Jsoup/parse s)))

(def counter (agent 0))

(defn claw []
	(let [conn (mg/connect {:host "127.0.0.1"})
				db (mg/get-db conn "web")
				coll "url"]
		(send counter
					(let [extractor (.newInstance ArticleExtractor)]
						(while true
							(let [{url :url} (mc/find-and-modify db coll {} {$set {:time (System/currentTimeMillis)}} {:sort (array-map :time 1, :_id 1)})]
								;(println "Get body for " url)
								(http/get url {:as :text}
													(fn [{:keys [body error] :as a}]
														;(println "Got body for " url)
														(if (not error)
															(time
																(let [parsed-html (html/html-resource (StringReader. body))]
																	(doseq [a-tag (html/select parsed-html [:a])]
																		(let [aurl (get-in a-tag [:attrs :href])]
																			(mc/insert db coll {:url aurl, :time (System/currentTimeMillis)})
																			))
																	(let [plain (strip-html-tags body), text (.getText extractor body), isa (not (clojure.string/blank? plain))]
																							 (if isa (mc/update db "page" {:uhash (digest/md5 url)} {$set {:html body, :text text, :isa (empty? text), :plain plain, :url url, :time (System/currentTimeMillis)}} {:upsert true})))))
															(println "Failed" error))
														))
								)))
					)))

(defn -main
	[& args]
	(claw)
	)



