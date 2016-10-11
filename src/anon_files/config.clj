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

(cc/defprop-optstr amqp-uri
  "The URI for the connection to the AMQP broker."
  [props config-valid configs]
  "amqp-uri" "amqp://guest:guest@rabbit:5672/")

(cc/defprop-optstr exchange-name
  "The name of the AMQP exchange to use."
  [props config-valid configs]
  "exchange-name" "de")

(cc/defprop-optboolean exchange-durable?
  "Whether or not the AMQP exchange is durable."
  [props config-valid configs]
  "exchange-durable" true)

(cc/defprop-optboolean exchange-auto-delete?
  "Whether or not the AMQP exchange will be automatically deleted."
  [props config-valid configs]
  "exchange-auto-delete" false)

(cc/defprop-optstr queue-name
  "The name of the AMQP queue."
  [props config-valid configs]
  "queue-name" "events.anon-files.queue")

(cc/defprop-optstr queue-durable?
  "Whether or now the queue is durable."
  [props config-valid configs]
  "queue-durable" true)

(cc/defprop-optstr queue-auto-delete?
  "Whether or not the queue is automatically deleted."
  [props config-valid configs]
  "queue-auto-delete" false)

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
