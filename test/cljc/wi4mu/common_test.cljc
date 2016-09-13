(ns wi4mu.common-test
  #? (:cljs (:require-macros [cljs.test :refer (is deftest testing)]))
  (:require [wi4mu.common :as sut]
            #?(:clj [clojure.test :refer :all]
               :cljs [cljs.test])))

(deftest example-passing-test-cljc
  (is (= 1 1)))
