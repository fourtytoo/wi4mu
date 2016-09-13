(ns wi4mu.test-runner
  (:require
   [doo.runner :refer-macros [doo-tests]]
   [wi4mu.core-test]
   [wi4mu.common-test]))

(enable-console-print!)

(doo-tests 'wi4mu.core-test
           'wi4mu.common-test)
