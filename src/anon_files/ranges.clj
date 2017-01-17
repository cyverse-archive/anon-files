(ns anon-files.ranges
   (:require [clojure.string :as string]))

(defn- trim-equals
  "Returns a string with the leading equals sign trimmed off. The
   entire header is trimmed of leading and trailing whitespace as a result."
  [header]
  (let [trimmed-header (string/trim header)]
    (if (.startsWith trimmed-header "=")
      (string/replace-first trimmed-header "=" "")
      trimmed-header)))

(defn- trim-bytes
  "Returns a string with the leading 'bytes' trimmed off. The entire header is trimmed
   of leading and trailing whitespace as a result."
  [header]
  (let [trimmed-header (string/trim header)]
    (if (.startsWith trimmed-header "bytes")
      (string/replace-first trimmed-header "bytes" "")
      trimmed-header)))

(defn- extract-byte-ranges
  "Returns a vector of tuples."
  [header]
  (re-seq #"[0-9]*\s*-\s*[0-9]*" header))

(defn- categorize-ranges
  "Categorize ranges based on whether they're a bounded range, an unbounded
   range, or a single byte request. The return value will be in the format:
       {
           :range \"rangestring\"
           :kind \"kind string\"
       }
   Values for :kind can be 'bounded', 'unbounded', 'unbounded-negative' or 'unknown'."
  [ranges]
  (let [mapify     (fn [range kind] {:range range :kind kind})
        bounded?   (fn [range] (re-seq #"[0-9]+\s*-\s*[0-9]+" range))
        unbounded? (fn [range] (re-seq #"[0-9]+\s*-\s*" range))
        end-byte?  (fn [range] (re-seq #"\s*-\s*[0-9]+" range))
        range-kind (fn [range]
                     (cond
                      (bounded? range)   "bounded"
                      (unbounded? range) "unbounded"
                      (end-byte? range)  "unbounded-negative"
                      :else              "unknown"))]
    (map #(mapify %1 (range-kind %1)) ranges)))

(defn- parse-ranges
  "Parses ranges based on type. A range of type will have an :lower and :upper field added,
   a range of type unbounded will have a :lower field and no :upper field. A field of :byte
   will have a :lower and :upper bound that is set to the same value. An unknown range will
   not have any fields added.

   The input should be a seq of maps returned by (categorize-ranges)."
  [ranges]
  (let [upper          (fn [range] (last (re-seq #"[0-9]+" (:range range))))
        lower          (fn [range] (first (re-seq #"[0-9]+" (:range range))))
        extract-byte   (fn [range] (first (re-seq #"\s*-\s*[0-9]+" (:range range))))
        bounded-type   (fn [range] (assoc range :upper (upper range) :lower (lower range)))
        unbounded-type (fn [range] (assoc range :lower (lower range)))
        unbounded-neg  (fn [range] (assoc range :lower (extract-byte range)))
        byte-type      (fn [range] (assoc range :upper (extract-byte range) :lower (extract-byte range)))
        delegate       (fn [range]
                         (case (:kind range)
                           "bounded"
                           (bounded-type range)

                           "unbounded"
                           (unbounded-type range)

                           "unbounded-negative"
                           (unbounded-neg range)

                           "byte"
                           (byte-type range)
                           range))]
    (map delegate ranges)))

(defn extract-ranges
  "Parses the range header and returns a list of ranges. The returned value will be the
   same as (parse-ranges)."
  [req]
  (-> (get-in req [:headers "range"])
      (trim-bytes)
      (trim-equals)
      (extract-byte-ranges)
      (categorize-ranges)
      (parse-ranges)))
