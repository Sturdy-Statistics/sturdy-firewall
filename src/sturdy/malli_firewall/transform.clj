(ns sturdy.malli-firewall.transform
  (:require
   [clojure.string :as string]
   [malli.core :as m]
   [malli.transform :as mt]
   [sturdy.malli-firewall.util :refer [full-name]]))

;; --- Logic adapted from your malli.error source ---

(defn- levenshtein [s1 s2]
  (let [s1 (str s1) s2 (str s2)]
    (peek (reduce (fn [previous current]
                    (reduce
                     (fn [row [diagonal above other]]
                       (let [update-val (if (= other current)
                                          diagonal
                                          (inc (min diagonal above (peek row))))]
                         (conj row update-val)))
                     [(inc (first previous))]
                     (map vector previous (next previous) s2)))
                  (map #(identity %2) (cons nil s2) (range))
                  s1))))

(defn- length-threshold [len]
  (condp #(<= %2 %1) len
    2 0
    5 1
    6 2
    11 3
    20 4
    (int (* 0.2 len))))

(defn- is-similar? [k1 k2]
  (let [s1 (some-> k1 full-name string/lower-case)
        s2 (some-> k2 full-name string/lower-case)]
    (if (and s1 s2)
      (let [min-len (min (count s1) (count s2))
            dist (levenshtein s1 s2)]
        (<= dist (length-threshold min-len)))
      false)))

(defn smart-key-transformer
  "Malli transformer that selectively keywordizes map keys.
  It interns only strings that exactly match schema keys. Near-miss strings
  are retained as strings so Malli can provide spelling suggestions.

  If `:strip-unknown-keys?` is truthy, all other keys are dropped. Otherwise,
  unknown keys are retained as strings, even if they were originally keywords.

  Prevents memory exhaustion (DoS) from arbitrary keyword interning
  while preserving typos for humanized error reporting."
  [{:keys [strip-unknown-keys?]
    :or {strip-unknown-keys? true}}]
  (mt/transformer
   {:decoders
    {:map
     {:compile (fn [schema _]
                 (let [valid-kw-keys (m/explicit-keys schema)
                       valid-str-keys (set (map full-name valid-kw-keys))]
                   {:enter
                    (fn [m]
                      (if (map? m)
                        (reduce-kv
                         (fn [acc k v]
                           (let [k-str (full-name k)]
                             (cond
                               ;; 1. Exact match (String or KW) -> Keywordize
                               (or (contains? valid-kw-keys k)
                                   (contains? valid-str-keys k-str))
                               (let [output-key (keyword k-str)]
                                 (if (contains? acc output-key)
                                   (throw
                                    (ex-info
                                     (str "Duplicate parameter: " output-key)
                                     {:type :duplicate-parameter
                                      :key output-key
                                      :input-keys #{output-key (full-name output-key)}}))
                                   (assoc acc output-key v)))

                               ;; 2. Smart typo check -> keep as original
                               (some #(is-similar? k-str %) valid-str-keys)
                               (assoc acc k v)

                               ;; 3. DOS Protection -> DROP (Strip)
                               :else
                               (if strip-unknown-keys?
                                 ;; drop the key
                                 acc
                                 ;; else, keep the key, but leave as a string
                                 (assoc acc k-str v)))))
                         {} m)
                        m))}))}}}))
