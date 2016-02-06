(ns plugin-helpers.core
  (:require [clojure.string :as s]
            [leiningen.core.main :as l]
            [plugin-helpers.format :as f]
            [rewrite-clj.zip :as z]))

(defn- pad-newline
  "Prepends a newline to the last item. Temporary fix because
  prepend/append-* operations overwrite spaces and newlines."
  [expr]
  (-> expr
      z/down
      z/rightmost
      z/prepend-newline
      (z/prepend-space 2)
      z/up))

(defn- insert-opt
  "Add a kv pair of options to the project map with a
  default indentation of 2 spaces."
  [project-map k v]
  (-> (z/append-child project-map k)
      pad-newline
      (z/append-child v)))

(defn- assoc-into
  "Builds a nested map using some keys ks and a nil value"
  [root ks]
  (reduce #(assoc {} %2 %)
    root (reverse ks)))

;; using callbacks for now to guarantee that each fn works well
(defn- assoc-default
  "A callback for assoc-in/with failures"
  [f m k ks]
  (let [v (f nil)
        node (z/node (f/format-expr (assoc-into v ks)))]
    (insert-opt m k node)))

(defn- remove-default
  "A callback for remove-in failures"
  [f m k ks]
  m)

(defn- conj-default [f m k ks]
  (let [v (z/sexpr (f nil))
        node (z/node (f/format-expr (assoc-into v ks)))]
    (insert-opt m k node)))

(defn- lookup-handler [f m ks & callback]
  (let [cback (or (first callback) conj-default)]
    (loop [f f m m [k & ks] ks]
      (if (empty? ks)
        (if-let [v (z/find-value m z/next k)]
          (-> v z/right f)
          ;; key not found and no ks
          (cback f m k nil))
        (if-let [v (z/find-value m z/next k)]
          (recur f (z/right v) ks)
          ;; when keys not found, put in a blank map
          (cback f m k ks))))))

(defn remove-in
  "Remove some key or key value pair in a file where ks is the
  path to the key."
  [ks]
  (fn [m]
    (lookup-handler (fn [zloc]
                      (if (z/map? (z/up zloc))
                        (-> zloc z/left z/remove z/right z/remove)
                        (-> zloc z/left z/remove)))
      m ks remove-default)))

(defn replace-in
  "Replace a string s nested in a file where ks is the
  path to the string. If the key in doesn't exist in the map
  already, it will be added and use the given string.

  example:
  (with-project (replace-in [:license :name] \"WTFPL\"))

  => {:project-stuff 'whatever
      :license {:name \"WTFPL\"}}"
  [ks s]
  (fn [m]
    (lookup-handler (fn [zloc]
                      (z/replace zloc s))
      m ks)))

(defn update-with
  "Similar to clojure update-in, this updates some value in a file.
  If there's no value for the key sequence then a map will be built
  containing a default value of applying f to nil.

  example:
  (with-project (update-with [:foo :bar] (constantly \"foobar\")))

  => {:project-stuff 'whatever
      :foo {:bar \"foobar\"}}"
  ([ks f]
   (fn [m]
     (lookup-handler (fn [zloc]
                       (if (nil? zloc)
                         (f nil)
                         (z/edit zloc f)))
       m ks assoc-default)))
  ([ks f args]
   (fn [m]
     (lookup-handler (fn [zloc]
                       (if (nil? zloc)
                         (f nil args)
                         (apply z/edit zloc f args)))
       m ks assoc-default))))

(defn assoc-with
  "Similar to Clojure's assoc-in, this will assoc a key value
  pair into some file, overwriting previous values if one is
  already present."
  [ks v]
  (fn [m]
    (lookup-handler (fn [zloc]
                      (if (nil? zloc)
                        v
                        (z/replace zloc
                          (z/node (f/format-expr v)))))
      m ks assoc-default)))

(defn with-file
  "Uses a zipper function f on some file"
  [filename f]
  (let [transformed (-> (z/of-file filename) f z/root-string)]
    (spit filename transformed)))

(defn with-project
  "Runs some function over the project.clj file"
  [f]
  ((partial with-file "project.clj") f))

;; special removal for project.clj because it's a list
(defn- remove-in*
  [ks]
  (fn [m]
    (lookup-handler (fn [zloc]
                      (-> zloc z/remove z/remove))
      m ks remove-default)))

(defn remove-in-project
  "Removes a key value pair from the project.clj."
  [ks]
  (with-project (remove-in* ks)))

(defn replace-in-project
  "Replace a string s nested in project.clj where ks is the
  path to the string. If the key in doesn't exist in the map
  already, it will be added and use the given string.

  example:
  (replace-in-project [:license :name] \"WTFPL\")

  => {:project-stuff 'whatever
      :license {:name \"WTFPL\"}}"
  [ks s]
  (with-project (replace-in ks s)))

(defn update-in-project
  "Similar to Clojure's update-in, this updates some value in project.clj.
  If there's no value for the key sequence then a map will be built
  containing a default value of applying f to nil.

  example:
  (update-in-project [:foo :bar] \"foobar\")

  => {:project-stuff 'whatever
      :foo {:bar \"foobar\"}}"
  ([ks f]
   (with-project (update-with ks f)))
  ([ks f & args]
   (with-project (update-with ks f args))))

(defn assoc-in-project
  "Similar to Clojure's assoc-in, this will assoc a key value
  pair into some project.clj, overwriting previous values if one is
  already present."
  [ks v]
  (with-project (assoc-with ks v)))


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
