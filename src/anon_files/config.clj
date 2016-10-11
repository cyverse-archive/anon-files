(ns anon-files.config
  (:use [slingshot.slingshot :only [throw+]])
  (:require [clojure-commons.config :as cc]
            [clojure-commons.error-codes :as ce]))

(def ^:private props
  "A ref for storing the configuration properties."
  (ref nil))

(def ^:private config-valid
  "A ref for storing the configuration validity flag."
  (ref true))

(def ^:private configs
  "A ref for storing the symbols used to get configuration settings."
  (ref []))

(cc/defprop-optint listen-port
  "The port to listen on for incoming requests."
  [props config-valid configs]
  "port" 60000)

(cc/defprop-optstr irods-host
  "The hostname or IP address to use when connecting to iRODS."
  [props config-valid configs]
  "irods-host" "irods")

(cc/defprop-optint irods-port
  "The port number to use when connecting to iRODS."
  [props config-valid configs]
  "irods-port" 1247)

(cc/defprop-optstr irods-zone
  "The name of the iRODS zone."
  [props config-valid configs]
  "irods-zone" "iplant")

(cc/defprop-optstr irods-home
  "The base path to the directory containign the home directories in iRODS."
  [props config-valid configs]
  "irods-home" "/irods/home")

(cc/defprop-optstr irods-user
  "The username to use when authenticating to iRODS."
  [props config-valid configs]
  "irods-user" "rods")

(cc/defprop-optstr irods-password
  "The password to use when authenticating to iRODS."
  [props config-valid configs]
  "irods-password" "notprod")

(cc/defprop-optstr anon-user
  "The username of the anonymous user. Usually just 'anonymous'."
  [props config-valid configs]
  "anon-user" "anonymous")

(defn pprint-to-string
  [m]
  (let [sw (java.io.StringWriter.)]
    (binding [*out* sw]
      (clojure.pprint/pprint m))
    (str sw)))

(defn- validate-config
  "Validates the configuration settings after they've been loaded."
  []
  (when-not (cc/validate-config configs config-valid)
    (throw+ {:error_code ce/ERR_CONFIG_INVALID})))

(defn load-config-from-file
  "Loads the configuration settings from a file."
  [cfg-path]
  (cc/load-config-from-file cfg-path props)
  (cc/log-config props)
  (validate-config))
