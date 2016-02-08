# plugin-helpers

Helper functions for writing Leiningen plugins.

Latest version:

[![Clojars Project](https://img.shields.io/clojars/v/com.greenyouse/plugin-helpers.svg)](https://clojars.org/com.greenyouse/plugin-helpers)

## Usage

This is mostly for my own use in writing Leiningen plugins so I'm
building it out as needed instead of trying for a comprehensive helper
library. The gist of it is a few functions for editing project.clj
files and handling command line Leiningen messages.

If you're interested in a feature either file an issue or send a PR. I'd
be happy to add more features if you have any in mind. 

You can see the full documentation
[here](http://greenyouse.github.io/plugin-helpers/index.html) but here's
a quick overview of the important parts:


### project.clj functions

For the next few examples we'll use a minimal project.clj config and
show how running each function updates its state.

```clj
{:description "foobar"
 :license {:name "Eclipse Public License"
           :url "http://www.eclipse.org/legal/epl-v10.html"}
 :dependencies [[org.clojure/clojure "1.8.0"]]}
```

The `remove-in-project` is basically a nested dissoc or disj. It removes
some item from the project file:

```clj
(remove-in-project [:description])
{:license {:name "Eclipse Public License"
           :url "http://www.eclipse.org/legal/epl-v10.html"}
 :dependencies [[org.clojure/clojure "1.8.0"]]} 

(remove-in-project [:license :name])
{:description "foobar"
 :license {:url "http://www.eclipse.org/legal/epl-v10.html"}
 :dependencies [[org.clojure/clojure "1.8.0"]]}

;; no change
(remove-in-project [:foo])
{:description "foobar"
 :license {:name "Eclipse Public License"
           :url "http://www.eclipse.org/legal/epl-v10.html"}
 :dependencies [[org.clojure/clojure "1.8.0"]]}
```

The `update-in-project` will work similar to Clojure's `update-in` by
applying some function at a given location:
```clj
(update-in-project [:description] (constantly "foo"))
{:description "foo"
 :license {:name "Eclipse Public License"
           :url "http://www.eclipse.org/legal/epl-v10.html"}
 :dependencies [[org.clojure/clojure "1.8.0"]]} 

;; adds to the map if not already there
(update-in-project [:foo] (constantly [1 2]))
{:description "foobar"
 :license {:name "Eclipse Public License"
           :url "http://www.eclipse.org/legal/epl-v10.html"}
 :dependencies [[org.clojure/clojure "1.8.0"]]
 :foo [1
       2]}
```

The `assoc-in-project` and (as you probably guessed) is similar to
Clojure's `assoc-in`: 

```clj
(assoc-in-project [:description] "foo")
{:description "foo"
 :license {:name "Eclipse Public License"
           :url "http://www.eclipse.org/legal/epl-v10.html"}
 :dependencies [[org.clojure/clojure "1.8.0"]]} 

(assoc-in-project [:dependencies] [[]])
{:description "foobar"
 :license {:name "Eclipse Public License"
           :url "http://www.eclipse.org/legal/epl-v10.html"}
 :dependencies [[]]}

;; adds to the map if not already there
(assoc-in-project [:foo] [1 2])
{:description "foobar"
 :license {:name "Eclipse Public License"
           :url "http://www.eclipse.org/legal/epl-v10.html"}
 :dependencies [[org.clojure/clojure "1.8.0"]]
 :foo [1 2]}
```

The last important project.clj function is `add-dependencies` which will
conj some dependency onto the project.clj dependency vector:

```clj
(add-dependencies [foo.bar "1.0.0"])
{:description "foo"
 :license {:name "Eclipse Public License"
           :url "http://www.eclipse.org/legal/epl-v10.html"}
 :dependencies [[org.clojure/clojure "1.8.0"]
                [foo.bar "1.0.0"]]}

(add-dependencies [a "0.1.0"] [b "0.1.0"])
{:description "foobar"
 :license {:name "Eclipse Public License"
           :url "http://www.eclipse.org/legal/epl-v10.html"}
 :dependencies [[org.clojure/clojure "1.8.0"]
                [a "0.1.0"]
                [b "0.1.0"]]}
```

There are a few more minor functions that are available too. Check the
docs or source code for more information.


### message functions

There are a few message helper functions for coloring text output on the
console. The `yellow-text` and `red-text` will output some text to the
console with the relevant ansi color codes. If you want another color,
you can wrap text with the `color-message` function and one of the
values from `ansi-codes`. 

There are also `warning`, `info`, and `abort` that are wrappers over
Leiningen functions of the same names but with color encoding.


## Continuous Integration
[![Build Status](https://travis-ci.org/greenyouse/plugin-helpers.svg?branch=master)](https://travis-ci.org/greenyouse/plugin-helpers)


## License

Copyright © 2016 Ed Babcock

Distributed under the Eclipse Public License version 1.0
