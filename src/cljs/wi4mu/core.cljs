(ns wi4mu.core
  (:require [reagent.core :as reagent :refer [atom]]
            [cljs.core.async :as async
             :refer [<! >! alts!]]
            [cljs-http.client :as http]
            [cljs-time.core :as time]
            [cljs-time.coerce :as timec]
            [cljs-time.format :as timef]
            [cljsjs.react-bootstrap]
            [cljsjs.fixed-data-table]
            [clojure.string :as string]
            [wi4mu.common :as util])
  (:require-macros [cljs.core.async.macros :refer [go go-loop]]))

(enable-console-print!)

(defonce search-string (atom ""))
(defonce message-list (atom []))
(defonce current-message (atom nil))
(def search-string-updates (async/chan (async/sliding-buffer 1)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn reply-body [reply]
  (let [{:keys [status success body] :as answer} reply]
    (when (and success (= 200 status))
      body)))

(defn rest-get [url parms]
  (let [chan (async/chan 1 (map reply-body))]
    (->> parms
         (assoc {:channel chan} :query-params)
         (http/get url))
    chan))

(def time-formatter
  (timef/formatters :mysql))

(defn format-time [time]
  (if (time/date? time)
    (timef/unparse time-formatter time)
    ""))

(defn fetch-message [id]
  (rest-get "/msg-data" {:id id :content-type :plain}))

(defn load-message [id message]
  (reset! message (str "Loading " id " ..."))
  (->> (fetch-message id)
       <!
       (reset! message)
       go))

(defn fetch-message-list [search-string]
  (->> (string/split search-string #"\s+")
       (assoc {} :query)
       (rest-get "/find")))

(defn load-message-list [query message-list]
  (reset! message-list (str "Searching for " query " ..."))
  (->> query
       fetch-message-list
       <!
       (reset! message-list)
       go))

(defn message-list-updater [message-list search-string-updates]
  (go-loop [search-string nil
            query-result (async/chan)]
    (let [[value c] (alts! [search-string-updates query-result (async/timeout 2000)])]
      (condp = c
        search-string-updates
        (do (cljs-http.core/abort! query-result)
            (recur value (async/chan)))

        query-result
        (do
          (reset! message-list value)
          (recur search-string (async/chan)))

        ;; timeout
        (do
          (cljs-http.core/abort! query-result)
          (if (nil? search-string)
            (recur nil query-result)
            (do
              (reset! message-list (str "Searching for " search-string " ..."))
              (recur nil (fetch-message-list search-string)))))))))

(message-list-updater message-list search-string-updates)

(defn text-input-component [search-string message-list]
  [:div {:id "search-input"}
   [:button {:on-click #(load-message-list @search-string message-list)} "search"]
   [:input {:type "text"
            :value @search-string
            :on-key-press (fn [e]
                            ;; Treat ENTER as a button press
                            (when (= 13 (.-charCode e))
                              (load-message-list @search-string message-list)))
            :on-change (fn [e]
                         (let [value (->> e .-target .-value)]
                           (async/put! search-string-updates value)
                           (reset! search-string value)))}]])

(defn print-message-body [body]
  [:pre (-> body first :body)])

(defn hdr= [h1 h2]
  (= (string/lower-case h1)
     (string/lower-case h2)))

(defn hdr [message header]
  (->> (get @message :headers)
       (filter (comp (partial hdr= header) first))
       first
       second))

(defn message-component [message]
  (fn []
    (cond (nil? @message) [:div]

          (string? @message) [:iframe {:src (str "/msg?id=" @message)
                                       :style {:width "100%" :height "100%"}}]

          :else [:div
                 [:div {:class "headers"}
                  [:table {:class "headers"}
                   (doall
                    (map (fn [[tag value]]
                           [:tr [:td tag] [:td value]])
                         (util/sort-headers (:headers @message))))]]
                 [:div {:class "message"}
                  [:pre (:body @message)]]])))

(reagent/render [text-input-component search-string message-list]
                (js/document.getElementById "search-entry"))

(reagent/render [message-component current-message]
                (js/document.getElementById "message"))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def Table (reagent/adapt-react-class js/FixedDataTable.Table))
(def Column (reagent/adapt-react-class js/FixedDataTable.Column))
(def Cell (reagent/adapt-react-class js/FixedDataTable.Cell))

(defn cell-args [jsargs]
  (js->clj jsargs :keywordize-keys true))

(defn cell-address [args]
  (let [{column :columnKey row :rowIndex} args]
    [row (keyword column)]))

(defn get-row-msgid [message-list row]
  (-> @message-list (nth row) :msgid))

(defn get-row-data [message-list row]
  (-> @message-list (nth row)))

(defn get-cell-data [message-list row column]
  (-> @message-list
      (nth row)
      (get (keyword column))))

(defn text-cell [message-list args]
  (let [args (cell-args args)
        [row col] (cell-address args)
        data (get-row-data message-list row)]
    (reagent/as-element [Cell {:on-click #(load-message (:msgid data) current-message)}
                         #_{:on-click #(reset! current-message (:msgid data))}
                         (get data col)])))

(defn date-cell [message-list args]
  (reagent/as-element [Cell (->> (cell-args args)
                                 cell-address
                                 (apply get-cell-data message-list)
                                 (* 1000)
                                 timec/from-long
                                 format-time)]))

(defn message-list-component [message-list]
  (let [mc (partial text-cell message-list)]
    (if (string? @message-list)
      [:div [:span @message-list]]
      [:div {:class "list"}
       (when @message-list
         [:span "messages: "
          (count @message-list)])
       [Table {:width        1000
               :height       200
               :rowHeight    25
               :rowsCount    (count @message-list)
               :headerHeight 30}
        [Column {:header "date" :cell #(date-cell message-list %) :columnKey :date :width 150 :fixed true}]
        [Column {:header "from" :cell mc :columnKey :from :width 200 :flexGrow 1}]
        [Column {:header "subject" :cell mc :columnKey :subject :width 450 :flexGrow 2}]]])))

(reagent/render [message-list-component message-list]
                (js/document.getElementById "message-list"))


