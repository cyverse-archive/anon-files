(ns anon-files.serve
  (:use [ring.util.response]
        [ring.util.time])
  (:require [clj-jargon.init :as init]
            [clj-jargon.item-ops :as ops]
            [clj-jargon.item-info :as info]
            [clj-jargon.permissions :as perms]
            [clj-jargon.paging :as paging]
            [cemerick.url :as url]
            [clojure.tools.logging :as log]
            [anon-files.config :as cfg]
            [anon-files.ranges :as ranges]
            [clojure.string :as string]
            [clojure.java.io :as io]
            [anon-files.inputs :as inputs]))

(def ^:private jargon-cfg
  (memoize
    #(init/init
       (cfg/irods-host)
       (str (cfg/irods-port))
       (cfg/irods-user)
       (cfg/irods-password)
       (cfg/irods-home)
       (cfg/irods-zone)
       "")))

(defn- range-request?
  [req]
  (and (contains? req :headers)
       (contains? (:headers req) "range")))

(defn- valid?
  [cm filepath]
  (cond
   (not (info/exists? cm filepath))
   (do (log/warn "[anon-files]" filepath "does not exist.")
       false)

   (not (info/is-file? cm filepath))
   (do (log/warn "[anon-files]" filepath "is not a file.")
       false)

   (not (perms/is-readable? cm (cfg/anon-user) filepath))
   (do (log/warn "[anon-files]" filepath "is not readable.")
       false)

   :else true))

(defmacro validated
  [cm filepath & body]
  `(cond
     (not (info/exists? ~cm ~filepath))
     (do (log/warn "[anon-files]" ~filepath "does not exist.")
       (not-found "Not found."))

     (not (info/is-file? ~cm ~filepath))
     (do (log/warn "[anon-files]" ~filepath "is not a file.")
       (-> (response "Not a file.") (status 403)))

     (not (perms/is-readable? ~cm (cfg/anon-user) ~filepath))
     (do (log/warn "[anon-files]" ~filepath "is not readable.")
       (-> (response "Not allowed.") (status 403)))

     :else
     ~@body))

(defn- base-file-header
  [filepath lastmod]
  {"Accept-Ranges"    "bytes"
   "Cache-Control"    "no-cache"
   "ETag"             (str "W/" lastmod)
   "Expires"          "0"
   "Vary"             "*"
   "Content-Location" filepath})

(defn- file-header
  ([filepath lastmod start-byte end-byte]
     (merge (base-file-header filepath lastmod)
            {"Content-Length" (str (inc (- end-byte start-byte)))})))

(defn- serve
  [cm filepath]
  (validated cm filepath (ops/input-stream cm filepath)))

(defn- not-satisfiable-response
  [cm filesize]
  {:status  416
   :body    (init/proxy-input-stream cm (io/input-stream (.getBytes "The requested range is not satisfiable.")))
   :headers {"Accept-Ranges" "bytes"
             "Content-Range" (str "bytes */" filesize)}})

(defn- range-body
  [cm filepath start-byte end-byte]
  (if (>= (- end-byte start-byte) 0)
    (inputs/chunk-stream cm filepath start-byte end-byte)))

(defn- handle-range-request
  [cm filepath {:keys [file-size lower upper num-bytes]}]
  (log/warn
     "File information:\n"
     "File Path:" filepath "\n"
     "File Size:" file-size "\n"
     "Lower Bound:" lower "\n"
     "Upper Bound:" upper "\n"
     "Number bytes:" num-bytes "\n")
  (cond
   (> lower upper)
   (not-satisfiable-response cm file-size)

   (>= lower file-size)
   (not-satisfiable-response cm file-size)

   (= lower upper)
   (range-body cm filepath lower upper)

   :else
   (range-body cm filepath lower upper)))

(defn- calc-lower
  [lower-val]
  (if-not (pos? lower-val)
    0
    lower-val))

(defn- calc-upper
  [upper-val file-size]
  (if (> upper-val file-size)
    file-size
    upper-val))

(defn- bounded-request-info
  [cm filepath range]
  (let [file-size (info/file-size cm filepath)
        retval {:file-size file-size
                :lastmod   (info/lastmod-date cm filepath)
                :lower     (calc-lower (Long/parseLong (:lower range)))
                :upper     (calc-upper (Long/parseLong (:upper range)) file-size)
                :kind      (:kind range)}]
    (assoc retval :num-bytes (inc (- (:upper retval) (:lower retval))))))

(defn- unbounded-request-info
  [cm filepath range]
  (let [file-size (info/file-size cm filepath)
        retval {:file-size file-size
                :lastmod   (info/lastmod-date cm filepath)
                :lower     (calc-lower (Long/parseLong (:lower range)))
                :upper     (dec file-size)
                :kind      (:kind range)}]
    (assoc retval :num-bytes (inc (- (:upper retval) (:lower retval))))))

(defn- unbounded-negative-info
  [cm filepath range]
  (let [file-size (info/file-size cm filepath)
        retval {:file-size file-size
                :lastmod   (info/lastmod-date cm filepath)
                :lower     (calc-lower (+ file-size (- (Long/parseLong (:lower range)) 1)))
                :upper     (calc-upper (- file-size 1) file-size)
                :kind      (:kind range)}]
    (assoc retval :num-bytes (inc (- (:upper retval) (:lower retval))))))

(defn- byte-request-info
  [cm filepath range]
  (let [file-size (info/file-size cm filepath)
        retval {:file-size file-size
                :lastmod   (info/lastmod-date cm filepath)
                :lower     (calc-lower (Long/parseLong (:lower range)))
                :upper     (calc-upper (+ (Long/parseLong (:lower range)) 1) file-size)
                :kind      (:kind range)}]
    (assoc retval :num-bytes (inc (- (:upper retval) (:lower retval))))))

(defn- normal-request-info
  [cm filepath range]
  (let [filesize (info/file-size cm filepath)]
    {:file-size filesize
     :lastmod   (info/lastmod-date cm filepath)
     :lower     0
     :upper     (dec filesize)
     :num-bytes filesize
     :kind      (:kind range)}))

(defn- request-info
  [cm filepath range]
  (case (:kind range)
    "bounded"
    (bounded-request-info cm filepath range)

    "unbounded"
    (unbounded-request-info cm filepath range)

    "unbounded-negative"
    (unbounded-negative-info cm filepath range)

    "byte"
    (byte-request-info cm filepath range)

    (normal-request-info cm filepath range)))

(defn- content-range-str
  [info]
  (let [kind  (:kind info)
        lower (:lower info)
        upper (:upper info)
        filesize (:file-size info)]
    (case kind
      "bounded"
      (str "bytes " lower "-" upper "/" filesize)

      "unbounded"
      (str "bytes " lower "-" filesize "/" filesize)

      "unbounded-negative"
      (str "bytes " lower "-" filesize "/" filesize)

      "byte"
      (str "bytes " lower "-" upper "/" filesize)

      (str "bytes " lower "-" upper "/" filesize))))

(defn- log-headers
  [response]
  (log/warn "Response map:\n" (dissoc response :body))
  response)

(defn- get-req-info
  ([req]
   (init/with-jargon (jargon-cfg) [cm]
     (get-req-info cm req)))
  ([cm req]
   (if-not (valid? cm (url/url-decode (:uri req)))
     (throw (Exception. "Bad")))
   (let [range (first (ranges/extract-ranges req))]
     (request-info cm (url/url-decode (:uri req)) range))))

(defn handle-request
  [req]
  (log/info "Handling GET request for" (:uri req))
  (log/info "\n" (cfg/pprint-to-string req))
  (let [filepath (url/url-decode (:uri req))]
    (try
      (if (range-request? req)
        (log-headers
          (init/with-jargon (jargon-cfg) :auto-close false [cm]
           (let [info     (get-req-info cm req)
                 body     (handle-range-request cm filepath info)]
             (if (map? body)
               body
               {:status  206
                :body    body
                :headers (assoc (file-header filepath (:lastmod info) (:lower info) (:upper info))
                           "Content-Range" (content-range-str info))}))))
        (init/with-jargon (jargon-cfg) [cm]
          (serve cm filepath)))
      (catch Exception e
        (log/warn e)))))

(defn handle-head-request
  [req]
  (log/info "Handling head request for" (:uri req))
  (log/info "\n" (cfg/pprint-to-string req))
  (let [filepath (url/url-decode (:uri req))]
    (init/with-jargon (jargon-cfg) [cm]
      (log-headers
       (validated cm filepath
                  (if (range-request? req)
                    (let [info (get-req-info cm req)]
                      {:status  200
                       :body    ""
                       :headers (assoc (file-header filepath (:lastmod info) (:lower info) (:upper info))
                                  "Content-Range"  (content-range-str info))})
                    (let [lastmod  (info/lastmod-date cm filepath)
                          filesize (info/file-size cm filepath)]
                      {:status 200
                       :body ""
                       :headers (file-header filepath lastmod 0 (dec filesize))})))))))

(defn- build-options-response
  [cm {:keys [uri]}]
  (let [lastmod  (info/lastmod-date cm uri)
        filesize (info/file-size cm uri)]
    {:status  200
     :body    ""
     :headers (assoc (base-file-header uri lastmod)
                "Allow" "GET, HEAD")}))

(defn handle-options-request
  [req]
  (log/info "Handling options request for" (:uri req))
  (log/info "\n" (cfg/pprint-to-string req))
  (init/with-jargon (jargon-cfg) [cm]
    (log-headers (validated cm (url/url-decode (:uri req)) (build-options-response cm req)))))
