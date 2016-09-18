(ns wi4mu.core
  (:require [reagent.core :as reagent :refer [atom]]
            [cljs.core.async
             :refer [put! <! >! alts! chan timeout]]
            [cljs-http.client :as http]
            [cljs-time.core :as time]
            [cljs-time.coerce :as timec]
            [cljs-time.format :as timef]
            [cljsjs.react-bootstrap]
            [cljsjs.fixed-data-table]
            [clojure.string :as string])
  (:require-macros [cljs.core.async.macros :refer [go go-loop]]))

(enable-console-print!)

(defonce search-string (atom ""))
(defonce message-list (atom []))
(defonce current-message (atom nil))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn fetch [url qparms]
  (let [c (chan)]
    (go (let [{:keys [status success body] :as answer} (<! (http/get url {:query-params qparms}))]
          (if (and success (= 200 status))
            (>! c body)
            (>! c nil))))
    c))

(def time-formatter
  (timef/formatters :mysql))

(defn format-time [time]
  (if (time/date? time)
    (timef/unparse time-formatter time)
    ""))

(defn fetch-message [id]
  (fetch "/msg" {:id id :content-type :plain}))

(defn load-message [id message]
  (reset! message (str "Loading " id " ..."))
  (->> (fetch-message id)
       <!
       (reset! message)
       go))

(defn load-message-list [query message-list]
  (reset! message-list (str "Searching for " query " ..."))
  (->> (string/split query #"\s+")
       (assoc {} :query)
       (fetch "/find")
       <!
       vec
       (reset! message-list)
       go))

(defn message-row [msg bgc]
  ^{:key (:msgid msg)}
  [:tr {;; :style {:backgroundColor bgc}
        :on-click #(load-message (:msgid msg) current-message)}
   [:td (format-time (timec/from-long (:date msg)))]
   [:td (:from msg)]
   [:td (:subject msg)]])

(defn text-input-component [search-string message-list]
  [:div
   [:button {:on-click #(load-message-list @search-string message-list)} "search"]
   [:input {:type "text"
            :width "60%"
            :value @search-string
            :on-key-press (fn [e]
                            ;; Treat ENTER as a button press
                            (when (= 13 (.-charCode e))
                              (load-message-list @search-string message-list)))
            :on-change #(->> % .-target .-value
                             (reset! search-string))}]
   ])

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
          (string? @message) [:div @message]
          :else [:div
                 [:table {:class "headers"}
                  (doall
                   (map (fn [tag]
                          [:tr [:td tag] [:td (hdr message tag)]])
                        ["date" "subject" "from" "to"]))]
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
                         (get data col)])))

(defn date-cell [message-list args]
  (reagent/as-element [Cell (->> (cell-args args)
                                 cell-address
                                 (apply get-cell-data message-list)
                                 timec/from-long
                                 format-time)]))

(defn message-list-component [message-list]
  (let [mc (partial text-cell message-list)]
    [:div {:class "list"}
     [Table {:width        1000
             :height       200
             :rowHeight    25
             :rowsCount    (count @message-list)
             :headerHeight 30}
      [Column {:header "date" :cell #(date-cell message-list %) :columnKey :date :width 150 :fixed true}]
      [Column {:header "from" :cell mc :columnKey :from :width 200 :flexGrow 1}]
      [Column {:header "subject" :cell mc :columnKey :subject :width 450 :flexGrow 2}]]]))

(reagent/render [message-list-component message-list]
                (js/document.getElementById "message-list"))
