(ns tekservix.smsgora-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [tekservix.smsgora :as sms])
  (:import (com.sun.net.httpserver HttpServer HttpHandler HttpExchange)
           (java.net InetSocketAddress)
           (javax.crypto Mac)
           (javax.crypto.spec SecretKeySpec)))

(def ^:private last-call (atom nil))
(def ^:private reply (atom {:status 200 :body "{\"data\":{}}"}))
(def ^:private base (atom nil))

(defn- start! []
  (let [server (HttpServer/create (InetSocketAddress. "127.0.0.1" 0) 0)]
    (.createContext server "/"
                    (reify HttpHandler
                      (handle [_ ex]
                        (let [^HttpExchange ex ex
                              body (slurp (.getRequestBody ex))
                              {:keys [status] ^String out :body} @reply
                              bytes (.getBytes out "UTF-8")]
                          (reset! last-call {:method (.getRequestMethod ex)
                                             :path (str (.getPath (.getRequestURI ex))
                                                        (some->> (.getRawQuery (.getRequestURI ex)) (str "?")))
                                             :key (.getFirst (.getRequestHeaders ex) "x-api-key")
                                             :body body})
                          (.add (.getResponseHeaders ex) "Content-Type" "application/json")
                          (.sendResponseHeaders ex (int status) (alength bytes))
                          (doto (.getResponseBody ex) (.write bytes) (.close))))))
    (.start server)
    (reset! base (str "http://127.0.0.1:" (.getPort (.getAddress server)) "/v1"))
    server))

(use-fixtures :once (fn [t] (let [s (start!)] (try (t) (finally (.stop ^HttpServer s 0))))))

(defn- c [] (sms/client "sgk_test" {:base-url @base}))

(deftest send-sms-test
  (reset! reply {:status 200 :body "{\"data\":{\"message_ids\":[1873],\"queued\":1}}"})
  (testing "simple form returns Clojure data"
    (is (= {:message_ids [1873] :queued 1} (sms/send-sms (c) "+250788123456" "Hi")))
    (is (= "POST" (:method @last-call)))
    (is (= "/v1/send-sms" (:path @last-call)))
    (is (= "sgk_test" (:key @last-call)))
    (is (re-find #"\+250788123456" (:body @last-call))))
  (testing "map form with phone and SIM"
    (sms/send-sms (c) {:to ["+250788123456" "+250722000000"] :message "Hi" :device-id 12 :sim-slot 1})
    (is (= "/v1/devices/12/send-sms" (:path @last-call)))
    (is (re-find #"\"sim_slot\":1" (:body @last-call)))))

(deftest bulk-uses-string-keys
  (reset! reply {:status 200 :body "{\"data\":{\"batch_id\":311}}"})
  (sms/send-bulk-sms (c) 12 [{:recipients ["+250788123456"] :message "Hi Aline"}] "Hello")
  (is (= "/v1/devices/12/send-bulk-sms" (:path @last-call)))
  (is (re-find #"\"recipients\":\[\"\+250788123456\"\]" (:body @last-call)))
  (is (not (re-find #":recipients" (:body @last-call)))))

(deftest reads-and-filters
  (reset! reply {:status 200 :body "{\"data\":{\"id\":5,\"status\":\"delivered\"}}"})
  (is (= "delivered" (:status (sms/get-message (c) 5))))
  (is (= "/v1/messages/5" (:path @last-call)))
  (sms/list-messages (c) {:status "failed" :limit 20})
  (is (re-find #"status=failed" (:path @last-call)))
  (is (re-find #"limit=20" (:path @last-call))))

(deftest errors-become-ex-info
  (reset! reply {:status 401 :body "{\"message\":\"Invalid API key\"}"})
  (let [e (try (sms/get-account (c)) nil (catch clojure.lang.ExceptionInfo e e))]
    (is (some? e))
    (is (= 401 (:status (ex-data e))))))

(deftest webhook-signature
  (let [secret "whsec_test" body "{\"event\":\"sms.received\"}"
        mac (doto (Mac/getInstance "HmacSHA256") (.init (SecretKeySpec. (.getBytes secret "UTF-8") "HmacSHA256")))
        hex (apply str (map #(format "%02x" %) (.doFinal mac (.getBytes body "UTF-8"))))]
    (is (true? (sms/verify-webhook body (str "sha256=" hex) secret)))
    (is (false? (sms/verify-webhook body "sha256=00" secret)))))
