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
					 org.apache.commons.lang.StringUtils
					 ))

(defn strip-html-tags
	"Function strips HTML tags from string."
	[s]
	(.text (org.jsoup.Jsoup/parse s)))

(defn check-abs-link [href base-url]
	(if (empty? href) nil
										(if (.contains href "://")
											href
											(str (StringUtils/join (drop-last (seq (.split (str (if (.equals (last base-url) \/) base-url (str base-url "/")) " ") "/"))) "/")
													 "/"
													 (if (.equals (first href) \/)
														 (rest href)
														 href)))))

(defn claw []
	(let [conn (mg/connect {:host "127.0.0.1"})
				db (mg/get-db conn "web")
				coll "url"]
		(let [extractor (.newInstance ArticleExtractor)]
			(while true
				(let [{url :url} (mc/find-and-modify db coll {} {$set {:time (System/currentTimeMillis)}} {:sort (array-map :time 1, :_id 1)})]
					;(println "Get body for " url)
					(http/get url {:as :auto}
										 (fn [{:keys [body error status] :as a}]
											 (println url)
											 (if (and (not error) (= status 200) (string? body))
												 (time
													 (let [parsed-html (html/html-resource (StringReader. body))]
														 (doseq [a-tag (html/select parsed-html [:a])]
															 (let [aurl (check-abs-link (get-in a-tag [:attrs :href]) url)]
																 (if (not (empty? aurl)) (mc/insert db coll {:url aurl, :time (System/currentTimeMillis)}))
																 ))
														 (let [plain (strip-html-tags body), text (.getText extractor body), isa (not (clojure.string/blank? text))]
															 (if isa (mc/update db "page" {:uhash (digest/md5 url)} {$set {:html body, :text text, :isa (empty? text), :plain plain, :url url, :time (System/currentTimeMillis)}} {:upsert true})))))
												 (println "Failed" error))
											 ))
					)))
		))

(defn -main
	[& args]
	(claw)
	)



