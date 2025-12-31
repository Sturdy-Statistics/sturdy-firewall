(ns sturdy.malli-firewall.util)

(defn full-name
  "Returns the string representation of a keyword (including namespace)
  or string. Returns nil for other types."
  [k]
  (cond
    (string? k) k
    (keyword? k) (if (namespace k)
                   (str (namespace k) "/" (name k))
                   (name k))
    :else nil))
