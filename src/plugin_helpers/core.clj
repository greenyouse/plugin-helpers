(ns plugin-helpers.core
  (:require [clojure.string :as s]
            [clojure.java.io :as io]
            [leiningen.core.main :as l]
            [rewrite-clj.zip :as z])
  (:import [java.io File]))


(defn- lookup-handler [f m [k & ks]]
  (if (empty? ks)
    (-> (z/find-value m z/next k) z/right f)
    (recur f (-> (z/find-value m z/next k) z/right) ks)))

(defn replace-in
  "Replace a string s nested in a file where ks is the
  path to the string

  example:
  (with-project (replace-in [:license :name] \"WTFPL\"))

  => {:project-stuff 'whatever
      :license {:name \"WTFPL\"}}"
  [s & ks]
  (fn [m]
    (lookup-handler (fn [zloc]
                      (z/replace zloc s))
      m ks)))

(defn update-with
  "Similar to clojure update-in, this updates some value in a file"
  [ks f & args]
  (fn [m]
    (lookup-handler (fn [zloc]
                      (z/edit zloc #(apply f % args)))
      m ks)))

(defn with-file
  "Uses a zipper funciton f on some file"
  [filename f]
  (let [transformed (-> (z/of-file filename) f z/root-string)]
    (spit filename transformed)))

(defn with-project
  "Runs some funciton over the project.clj file"
  [f]
  ((partial with-file "project.clj") f))

(defn- insert-dep
  "Inserts a dep into the dependencies vector"
  [dependecies dep]
  (-> dependecies
      (z/insert-right dep)
      z/append-newline
      z/right
      (z/prepend-space 16)))

(defn- conj-deps
  [& deps]
  (fn [m]
    (lookup-handler (fn [zloc]
                      (let [dependencies (-> zloc z/down z/rightmost)]
                        (reduce insert-dep dependencies deps)))
      m [:dependencies])))

(defn add-dependencies
  "Conj dependencies onto a project

  example:
  (add-dependencies [foo \"1.0.0\"] [bar \"0.4.5\"])

  => [[prexisting-deps]
      [foo \"1.0.0\"]
      [bar \"0.4.5\"]]"
  [& deps]
  (with-project (conj deps deps)))

(defn select-value
  "Selects a value from a file where ks is the path to the value"
  [filename & ks]
  (let [m (z/of-file filename)]
    (lookup-handler z/value m ks)))

(defn get-project-value
  "Same as select value, just uses project.clj by default"
  [& ks]
  (let [p-seq (-> "project.clj" slurp read-string (nthrest 3))
        p-map (apply hash-map p-seq)]
    (get-in p-map ks)))

(defn get-project-name
  "Returns the name of the project as a string"
  []
  (-> (z/of-file "project.clj") z/down z/right z/string))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Text Formatting

;; ansi-codes from lein-auto
(def ansi-codes
  {:reset   "\u001b[0m"
   :black   "\u001b[30m" :gray           "\u001b[1m\u001b[30m"
   :red     "\u001b[31m" :bright-red     "\u001b[1m\u001b[31m"
   :green   "\u001b[32m" :bright-green   "\u001b[1m\u001b[32m"
   :yellow  "\u001b[33m" :bright-yellow  "\u001b[1m\u001b[33m"
   :blue    "\u001b[34m" :bright-blue    "\u001b[1m\u001b[34m"
   :magenta "\u001b[35m" :bright-magenta "\u001b[1m\u001b[35m"
   :cyan    "\u001b[36m" :bright-cyan    "\u001b[1m\u001b[36m"
   :white   "\u001b[37m" :bright-white   "\u001b[1m\u001b[37m"
   :default "\u001b[39m"})

(defn- wrap-text [color msg]
  (let [reset (ansi-codes :reset)]
    (str color msg reset)))

(defn color-message
  "Formats one or more messages with a given color"
  [color]
  {:pre [(or (contains? ansi-codes color)
             (throw (Exception. (format "Unknown color %s, options are: %s"
                                  color (keys ansi-codes)))))]}
  (let [code (ansi-codes color)]
    (fn [msg & messages]
      (if messages
        (reduce #(conj % (wrap-text code %2))
          [] messages)
        (wrap-text code msg)))))

;; common colors for messages
(def red-text
  (color-message :red))

(def yellow-text
  (color-message :yellow))

(defn warning
  "Emit a warning in red text."
  [msg]
  (l/warn (red-text msg)))

(defn info
  "Display some info in yellow text."
  [msg]
  (l/info (yellow-text msg)))

(defn abort
  "Abort the process and show a message in red text."
  [msg]
  (l/info (red-text msg)))
