(ns plugin-helpers.format
  "Functions for writing Clojure code to output files. Each output
  gets formatted with reasonable indentations and newlines. Slightly
  optimized for project.clj files (see format-nested-seq)."
  (:require [clojure.string :as s]
            [rewrite-clj.zip :as z]))


(defn- zip-count [zloc]
  (loop [zloc (z/down zloc) acc 0]
    (if (z/end? zloc)
      acc
      (recur (z/right zloc) (inc acc)))))

(defn- zip-count-map [zloc]
  (/ (zip-count zloc) 2))

(defn- parse-seq?
  [zloc]
  (and (or (z/vector? zloc)
           (z/list? (z/down zloc))
           (z/set? zloc))
       (< 1 (zip-count zloc))))

(defn- parse-map?
  [zloc]
  (and (z/map? zloc)
       (< 1 (zip-count-map zloc))))

(defn- align-map
  "Handles the whitespace formatting for child maps."
  [child-v n]
  (-> child-v
    z/prepend-newline
    (z/prepend-space (inc n)) ; drop child map to newline
    ;; either use 1 space or the given n spaces
    z/append-newline
    z/right))

(defn- align-end-map
  "Formats whitespaces for maps that are end entries"
  [child-v n]
  (-> child-v
    ;; add a newline + indent for the map
    z/prepend-newline
    (z/prepend-space (inc n))
    z/up))

(defn- next-key
  "For adding newline + ws"
  [zloc n]
  (if (or (= 1 n) (zero? n))
    (-> zloc z/append-newline z/right)
    (-> zloc (z/append-space n) z/append-newline z/right )))

(declare format-seq)

(defn format-map
  "Takes a zipper of a map and adds newlines + indentations so
  the output looks nice. Child maps are put onto new lines,
  child seqs are kept on the same line, and key-value pairs are
  put onto new lines."
  ([zloc] (format-map zloc 1))
  ([zloc n]
   (loop [loc (z/down zloc)
          parent zloc]
     (if (z/end? loc)
       parent
       (let [v (z/right loc)
             k2 (z/right v)]
         (cond
           ;; child seq + not last kv pair
           ;; (parse-seq? m) (if )

           ;; child map + last kv pair, no newline
           (and (z/end? k2) (parse-map? v))
           (let [child-v (format-map v (inc n))]
             (recur (z/right child-v)
                    (align-end-map child-v n)))

           ;; child map + not last kv pair
           ;; newline after key and after value
           (parse-map? v)
           (let [child-v (format-map v (inc n))]
             (recur (align-map child-v n)
               (z/up v)))

           ;; kv at end of map, no newline
           (z/end? k2) (recur (z/right v)
                         (z/up v))

           ;; normal kv pair, newline
           :default
           (recur (next-key v n)
             (z/up v))))))))

;; same as format seq except for the default case not adding newlines
;; for dep entries like [[org.clojure/clojure "1.8.0"] [other "0.0.1"]]
(defn- format-nested-seq [zloc n]
  (loop [loc (z/down zloc)
         parent zloc]
    (if (z/end? loc)
      parent
      (let [x (z/right loc)]
        (cond
          ;; child seq + last, no spacing
          (and (z/end? x) (parse-seq? loc))
          (let [child-v (format-nested-seq loc (inc n))]
            (recur (z/right child-v)
                   (z/up child-v)))

          ;; child seq + not last, no spacing
          (parse-seq? loc)
          (let [child-v (format-nested-seq loc (inc n))]
            (recur (next-key child-v n)
              (z/up loc)))

          ;; child map + last kv pair, no newline
          (and (z/end? x) (parse-map? loc))
          (let [child-v (format-map loc (inc n))]
            (recur (z/right child-v)
              (align-end-map child-v n)))

          ;; child map + not last kv pair
          ;; newline after key and after value
          (parse-map? loc)
          (let [child-v (format-map loc (inc n))]
            (recur (align-map child-v n)
              (z/up loc)))

          :default
          (recur (z/right loc)
            (z/up loc)))))))

(defn format-seq
  "Takes a zipper of a vector/list/set and adds newlines + indentations
  so the output looks nice. Child maps are put onto new lines,
  child seqs are kept on the same line, and values are put onto new lines."
  ([zloc] (format-seq zloc 0))
  ([zloc n]
   (loop [loc (z/down zloc)
          parent zloc]
     (if (z/end? loc)
       parent
       (let [x (z/right loc)]
         (cond
           ;; child seq + last value,
           ;; format nested seq and
           (and (z/end? x) (parse-seq? loc))
           (let [child-v (format-nested-seq loc n)]
             (recur (z/right child-v)
                    (z/up child-v)))

           ;; child seq, add a newline
           (parse-seq? loc)
           (let [child-v (format-nested-seq loc n)]
             (recur (z/right (z/append-newline child-v))
                    (z/up loc)))

           ;; child map + last kv pair, no newline
           (and (z/end? x) (parse-map? loc))
           (let [child-v (format-map loc (inc n))]
             (recur (z/right child-v)
                    (align-end-map child-v n)))

           ;; child map + not last kv pair
           ;; newline after key and after value
           (parse-map? loc)
           (let [child-v (format-map loc (inc n))]
             (recur (align-map child-v n)
                    (z/up loc)))

           ;; last value or map coming up, no spacing
           (or (z/end? x) (parse-seq? x) (parse-map? x))
           (recur (z/right loc)
                  (z/up loc))

           ;; normal or end value, newline
           :default
           (recur (next-key loc n)
                  (z/up loc))))))))

(defn format-expr
  "Converts a normal Clojure value into a properly spaced value for
  output to a file. The indent is how many spaces the expression should
  be indented (default of 0)."
  ([expr] (format-expr expr 0))
  ([expr indent]
   (let [zloc (-> expr (s/replace #"," "") z/of-string)
         n (inc indent)]
     (cond
       (parse-map? zloc) (format-map zloc n)
       (parse-seq? zloc) (format-seq zloc n)
       :else
       zloc))))
