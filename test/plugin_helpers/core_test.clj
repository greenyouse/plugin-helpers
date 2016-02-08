(ns plugin-helpers.core-test
  (:require [clojure.test :refer :all]
            [plugin-helpers.core :refer :all]
            [rewrite-clj.zip :as z]))

(defn zip->clj
  "Helper fn for coercing a zipper to normal clojure"
  [expr]
  (-> expr z/root-string read-string))

(deftest remove-in-test
  (let [zmap (z/of-string "{:foo \"bar\"
                           :a {:b {:c 1}}
                           :fizz {:buzz [1 2 3]}}")]
    (are [expected ks]
        (= expected (zip->clj ((remove-in ks) zmap)))
      {:a {:b {:c 1}}
       :fizz {:buzz [1 2 3]}} [:foo]

      {:foo "bar"
       :a {:b {}}
       :fizz {:buzz [1 2 3]}} [:a :b :c]

      {:foo "bar"
       :a {:b {:c 1}}
       :fizz {:buzz [1 2]}} [:fizz :buzz 3]

      {:foo "bar"
       :a {:b {:c 1}}
       :fizz {:buzz [1 2 3]}} [:woot])))

(deftest update-with-test
  (let [zmap (z/of-string "{:foo \"bar\"
                           :a {:b {:c \"d\"}}
                           :fizz {:buzz [1 2 3]}}")]
    (are [expected ks f]
        (= expected (zip->clj ((update-with ks f) zmap)))
      {:foo "new"
       :a {:b {:c "d"}}
       :fizz {:buzz [1 2 3]}} [:foo] (constantly "new")

      {:foo "bar"
       :a {:b {:c "d"}}
       :fizz {:buzz [2 3 4]}} [:fizz :buzz] #(map inc %)

      {:foo "bar"
       :a {:b {:c "new"}}
       :fizz {:buzz [1 2 3]}} [:a :b :c] (constantly "new")

      {:foo "bar"
       :a {:b {:c "d" :d 1}}
       :fizz {:buzz [1 2 3]}} [:a :b :d] (constantly 1))))

(deftest assoc-with-test
  (let [zmap (z/of-string "{:foo \"bar\"
                           :a {:b {:c 1}}
                           :fizz {:buzz [1 2 3]}}")]
    (are [expected ks v]
        (= expected (zip->clj ((assoc-with ks v) zmap)))
      {:foo "new"
       :a {:b {:c 1}}
       :fizz {:buzz [1 2 3]}} [:foo] "new"

      {:foo "bar"
       :a {:b {:c 1 :d 2}}
       :fizz {:buzz [1 2 3]}} [:a :b :d] 2

      {:foo "bar"
       :a {:b {:c 2}}
       :fizz {:buzz [1 2 3]}} [:a :b :c] 2

      {:foo "bar"
       :a {:b {:c 1}}
       :fizz {:buzz [5 2 3]}} [:fizz :buzz 1] 5)))
