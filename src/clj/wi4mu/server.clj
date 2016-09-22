(ns wi4mu.server
  (:require [clojure.java.io :as io]
            [compojure.core :refer [ANY GET PUT POST DELETE defroutes]]
            [compojure.route :as route]
            [ring.middleware.defaults :refer [wrap-defaults api-defaults]]
            [ring.middleware.gzip :refer [wrap-gzip]]
            [ring.middleware.logger :refer [wrap-with-logger]]
            [ring.middleware.content-type :refer [wrap-content-type]]
            [org.httpkit.server :refer [run-server]]
            [ring.middleware.format :refer [wrap-restful-format]]
            [ring.util.response :as response]
            [clojure.java.shell :refer [sh]]
            [environ.core :refer [env]]
            [clojure.xml :as xml]
            [clojure.data.xml :as dxml]
            [clojure.zip :refer [xml-zip]]
            [clojure.data.zip :as dz]
            [clojure.data.zip.xml :as dzx]
            [clojure.string :as string]
            [clojure-mail.core :as mail]
            [clojure-mail.message :as message]
            [hiccup.core :refer [html]]
            [wi4mu.common :as util])
  (:gen-class))

(defn columns [tags]
  (fn [message]
    (list
     (into {} (map (juxt identity
                         #(dzx/xml1-> message % dzx/text))
                   tags)))))

(defn extract-msg-data [xml tags]
  (dzx/xml-> (xml-zip xml)
             :message
             (columns tags)))

(defn mu-find [& query]
  (let [{:keys [exit out] :as result} (apply sh "mu" "find" "--format" "xml" "-s" "date" "--reverse" "--" query)]
    (cond (zero? exit) (dxml/parse-str out)
          (= exit 4) nil
          :else (throw (ex-info "MU failed" result)))))

(defn find-messages [query]
  (->> (extract-msg-data (apply mu-find (if (vector? query) query [query]))
                         [:msgid :date :from :subject])
       (map #(update % :date (fn [str] (. Integer parseInt str))))))

(defn get-message-pathname [id]
  (-> (mu-find (str "msgid:" id))
      (extract-msg-data [:path])
      first
      :path))

(defn get-message [id]
  (-> id
      get-message-pathname
      mail/file->message))

(defn message-headers [msg]
  (map (comp first (partial into []))
       (clojure-mail.message/message-headers msg)))

(defn mime-type [type-string]
  (map keyword (-> type-string
                   (string/split #";")
                   first
                   string/lower-case
                   (string/split #"/"))))

(defn extract-message-body [msg content-type]
  (let [ctype (mime-type (message/content-type msg))]
    (case ctype
      [:multipart :alternative]
      (let [part (first (or (not-empty
                             (filter (fn [part]
                                       (-> part
                                           :content-type
                                           mime-type
                                           (= content-type)))
                                     (message/message-parts msg)))
                            (message/message-parts msg)))]
        [(:body part) (:content-type part)])

      [:multipart :mixed]
      [(message/message-parts msg) (message/content-type msg)]
      
      [(:body msg) (message/content-type msg)])))

(defn message->edn [msg content-type]
  (let [[body content-type] (extract-message-body msg content-type)]
    {:headers (message-headers msg)
     :body body
     :content-type content-type}))

(defn hdr= [h1 h2]
  (= (string/lower-case h1)
     (string/lower-case h2)))

(defn in? 
  "true if coll contains elm"
  [coll elm]  
  (some #(= elm %) coll))

(defn message->html [msg]
  (let [[body content-type] (extract-message-body msg :plain)
        headers (util/sort-headers (message-headers msg))
        hdr (fn [[tag value]]
              [:tr [:td tag] [:td value]])]
    [:html
     [:head
      [:link {:rel "stylesheet" :href "css/style.css" :type "text/css"}]]
     [:body
      [:div {:class "headers"}
       [:table {:class "headers"}
        (doall
         (map hdr headers))]]
      [:div {:class "message"}
       [:pre body]]]]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn edn-response [body]
  (-> (pr-str body)
      response/response
      (response/content-type "application/edn")))

(defn html-response [body]
  (-> (html body)
      response/response
      (response/content-type "text/html")))

(defn txt-response [body]
  (-> (str body)
      response/response
      (response/content-type "text/plain")))

(defn msg-data-response [msg ctype]
  (edn-response (message->edn msg ctype)))

(defroutes routes
  (GET "/" _
       (response/redirect "/index.html"))
  (GET "/find" [query]
       (edn-response (find-messages query)))
  (GET "/msg" [id content-type]
       (-> id get-message message->html html-response))
  (GET  "/msg-data" [id content-type]
        (msg-data-response (get-message id)
                           (keyword (or content-type :plain))))
  ;; this will serve the main page, the css style files and all the rest
  (route/resources "/")
  (route/not-found "Not Found"))

(defn wrap-exception [f]
  (fn [request]
    (try (f request)
      (catch Exception e
        {:status 500
         :body (or (ex-data e) e)}))))

(def http-handler
  (-> routes
      wrap-exception
      wrap-restful-format
      (wrap-defaults api-defaults)
      wrap-content-type
      wrap-with-logger
      wrap-gzip))

(defonce stop-server (atom (fn [])))
(def ^:dynamic *default-port* 10555)

(defn start-server [& [port]]
  (let [port (Integer. (or port (env :port) *default-port*))]
    (@stop-server)
    (reset! stop-server (run-server http-handler {:port port :join? false}))))

(defn -main [& [port]]
  (start-server port))
