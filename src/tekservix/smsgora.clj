(ns tekservix.smsgora
  "SMS Gora SMS Gateway: Clojure client by Tekservix.

  Send and receive SMS through your own Android phones. A thin wrapper over the
  official Java client (com.tekservix/smsgora on Maven Central); every call returns
  plain Clojure data (maps with keyword keys, vectors).

  You need a Tekservix account with SMS Gora and an API key (it starts with sgk_):
  https://tekservix.com/smsgora"
  (:require [clojure.walk :as walk])
  (:import (com.tekservix.smsgora SmsGora SmsGoraException)
           (com.google.gson JsonElement JsonObject JsonPrimitive)))

(set! *warn-on-reflection* true)

(defn json->clj
  "Turns a Gson JsonElement into Clojure data."
  [^JsonElement e]
  (cond
    (or (nil? e) (.isJsonNull e)) nil
    (.isJsonObject e) (into {} (map (fn [[k v]] [(keyword k) (json->clj v)]))
                            (.entrySet ^JsonObject (.getAsJsonObject e)))
    (.isJsonArray e) (mapv json->clj (.getAsJsonArray e))
    :else (let [^JsonPrimitive p (.getAsJsonPrimitive e)]
            (cond (.isBoolean p) (.getAsBoolean p)
                  (.isNumber p) (let [n (.getAsNumber p)
                                      d (.doubleValue n)]
                                  (if (and (== d (Math/rint d)) (< (Math/abs d) 9.0E15))
                                    (long d)
                                    d))
                  :else (.getAsString p)))))

(defn- stringify
  "Clojure data -> what Gson serialises cleanly: string keys all the way down, keywords as names."
  [x]
  (when x
    (walk/postwalk (fn [v] (cond (map? v) (into {} (map (fn [[k vv]] [(if (keyword? k) (name k) (str k)) vv])) v)
                                 (keyword? v) (name v)
                                 :else v))
                   x)))

(defn client
  "Creates a client. Options: :base-url, :timeout-ms (default 30000).

  (def sms (client (System/getenv \"SMSGORA_API_KEY\")))"
  (^SmsGora [api-key] (SmsGora. ^String api-key))
  (^SmsGora [api-key {:keys [base-url timeout-ms] :or {base-url SmsGora/DEFAULT_BASE_URL timeout-ms 30000}}]
   (SmsGora. ^String api-key ^String base-url (int timeout-ms))))

(defmacro ^:private call [form]
  `(try (json->clj ~form)
        (catch SmsGoraException e#
          (throw (ex-info (.getMessage e#) {:status (.getStatus e#)} e#)))))

(defn send-sms
  "Sends one text to one or more numbers.

  (send-sms sms \"+250788123456\" \"Your order has shipped\")
  (send-sms sms {:to [\"+250788123456\" \"+250722000000\"] :message \"Hi\"
                 :device-id 12 :sim-slot 1 :schedule-at \"2026-12-01T08:00:00Z\"})"
  ([^SmsGora c to message] (call (.sendSms c ^String to ^String message)))
  ([^SmsGora c {:keys [to message device-id sim-slot schedule-at]}]
   (call (.sendSms c ^java.util.List (vec (if (string? to) [to] to)) ^String message
                   (some-> device-id long Long/valueOf) (some-> sim-slot int Integer/valueOf)
                   ^String schedule-at))))

(defn send-bulk-sms
  "Different texts for different people in one call (up to 5,000 recipients).
  messages: [{:recipients [\"+250788123456\"] :message \"Hi Aline\"} {:recipients [\"+250722000000\"]}]"
  [^SmsGora c device-id messages & [message-template]]
  (call (.sendBulkSms c (long device-id) ^java.util.List (mapv stringify messages) ^String message-template)))

(defn get-message "One message with its delivery status." [^SmsGora c id] (call (.getMessage c (long id))))
(defn cancel-message "Cancels a queued or scheduled message." [^SmsGora c id] (call (.cancelMessage c (long id))))
(defn list-messages "Messages, filtered: {:direction \"out\" :status \"failed\" :limit 20}."
  [^SmsGora c & [filters]] (call (.listMessages c ^java.util.Map (stringify filters))))
(defn list-received-sms "Incoming SMS for one phone (or all with nil device-id)."
  [^SmsGora c & [device-id filters]]
  (call (.listReceivedSms c (some-> device-id long Long/valueOf) ^java.util.Map (stringify filters))))
(defn get-batch "A bulk batch." [^SmsGora c id] (call (.getBatch c (long id))))
(defn list-devices "Your linked phones." [^SmsGora c] (call (.listDevices c)))
(defn get-device "One phone." [^SmsGora c id] (call (.getDevice c (long id))))
(defn get-account "Plan, limits and usage." [^SmsGora c] (call (.getAccount c)))
(defn list-webhooks "Your webhooks." [^SmsGora c] (call (.listWebhooks c)))
(defn create-webhook "Adds a webhook. events default to [\"sms.received\"]."
  [^SmsGora c url & [events device-id]]
  (call (.createWebhook c ^String url ^java.util.List (vec (or events ["sms.received"]))
                        (some-> device-id long Long/valueOf))))
(defn delete-webhook "Removes a webhook." [^SmsGora c id] (call (.deleteWebhook c (long id))))
(defn request "Any other API call: (request sms :get \"/batches/311\")."
  [^SmsGora c method path & [body]]
  (call (.request c (.toUpperCase (name method)) ^String path (some-> body stringify))))

(defn verify-webhook
  "Checks the X-SmsGora-Signature header (HMAC-SHA256 of the raw body) before you trust a webhook call."
  [raw-body signature secret]
  (if (bytes? raw-body)
    (SmsGora/verifyWebhook ^bytes raw-body ^String signature ^String secret)
    (SmsGora/verifyWebhook ^String (str raw-body) ^String signature ^String secret)))
