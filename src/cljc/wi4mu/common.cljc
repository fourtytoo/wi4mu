(ns wi4mu.common
  (:require [clojure.string :as string]))

(def important-headers #{"date" "from" "to" "subject"})

(defn sort-headers
  "Sort headers placing important headers first."
  [headers]
  (let [{important true, unimportant nil}
        (group-by (fn [h]
                    (some #(-> (first h) string/lower-case (= %)) important-headers))
                  headers)]
    (concat important unimportant)))
