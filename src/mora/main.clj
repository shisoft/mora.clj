(ns mora.main (:gen-class)
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
											 org.apache.commons.lang.StringUtils))

(defn strip-html-tags
	"Function strips HTML tags from string."
	[s]
	(.text (org.jsoup.Jsoup/parse s)))

(defn abs-link [href base-url]
	(if (seq href)
		(if (.contains href "://")
			href
			(let [remove-blank (partial remove clojure.string/blank?)
						drop-all-upwards (partial drop-while (partial = ".."))

						base-url-blocks*
						(remove-blank
							((if (= (last base-url) \/) identity drop-last)
							 (clojure.string/split base-url #"/")))
						base-url-blocks (reverse (drop-all-upwards (reverse base-url-blocks*)))
						base-url-blocks (drop-last (- (count base-url-blocks*) (count base-url-blocks)) base-url-blocks)

						href-blocks* (remove-blank (clojure.string/split href #"/"))
						href-blocks (drop-all-upwards href-blocks*)
						base-url-blocks (drop-last (- (count href-blocks*) (count href-blocks)) base-url-blocks)]

				(clojure.string/join "/" (concat base-url-blocks (drop-while (partial = ".") href-blocks)))))))

(defn check-and-insert-linked-urls! [url body db coll]
	(doseq [a-tag (html/select (html/html-resource (StringReader. body))
														 [:a :attrs :href]) ]
		(when-let [aurl (seq (abs-link a-tag url))]
			(try
				(mc/insert db coll {:url (apply str aurl), :time (System/currentTimeMillis)})
				(catch Exception e (println (.getMessage e)))))))

(defn strip-text-and-insert! [url body db coll extractor]
	(let [plain (strip-html-tags body)
				text (.getText extractor body)]
		(when-not (clojure.string/blank? text)
			(mc/update db "page"
								 {:uhash (digest/md5 url)}
								 {$set {:html body, :text text, :isa (empty? text), :plain plain, :url url, :time (System/currentTimeMillis)}}
								 {:upsert true}))))

(defn claw []
	(let [conn (mg/connect {:host "127.0.0.1"})
				db (mg/get-db conn "web")
				coll "url"]
		(let [extractor (.newInstance ArticleExtractor)]
			(while true
				(let [url (:url (mc/find-and-modify db coll {} {$set {:time (System/currentTimeMillis)}} {:sort (array-map :time 1, :_id 1)}))

							process-page
							(fn [{:keys [body error status]}]
								(println url)
								(if (and (not error) (= status 200) (string? body))
									(do
										(check-and-insert-linked-urls! url body db coll)
										(strip-text-and-insert! url body db coll extractor))
									(println "Failed" error)))]
					(http/get url {:as :auto} process-page))))))

(defn -main [& args]
	(claw))