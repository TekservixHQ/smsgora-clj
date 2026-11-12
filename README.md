# SMS Gora SMS Gateway: Clojure client

**SMS Gora SMS Gateway** - Clojure client by Tekservix. Send and receive SMS through your own Android phones, with delivery reports and signed webhooks.

**You need a Tekservix account with SMS Gora and at least one Android phone linked with the SMS Gora app.** [Create an account](https://tekservix.com/smsgora) · [API docs](https://tekservix.com/smsgora/docs/)

A thin wrapper over the official Java client ([com.tekservix/smsgora](https://central.sonatype.com/artifact/com.tekservix/smsgora) on Maven Central). Every call returns plain Clojure data.

## Install

deps.edn:

```clojure
com.tekservix/smsgora-clj {:mvn/version "1.0.0"}
```

Leiningen:

```clojure
[com.tekservix/smsgora-clj "1.0.0"]
```

## Use

```clojure
(require '[tekservix.smsgora :as sms])

(def c (sms/client (System/getenv "SMSGORA_API_KEY")))   ; key starts with sgk_

(sms/send-sms c "+250788123456" "Your order #1042 has shipped.")
;; => {:message_ids [1873] ...}

;; From a chosen phone and SIM, later
(sms/send-sms c {:to ["+250788123456" "+250722000000"] :message "Reminder: appointment tomorrow"
                 :device-id 12 :sim-slot 1 :schedule-at "2026-12-01T08:00:00Z"})

;; Different texts for different people
(sms/send-bulk-sms c 12 [{:recipients ["+250788123456"] :message "Hi Aline, parcel #1042 is ready."}
                         {:recipients ["+250722000000"]}]
                   "Your parcel is ready.")

(sms/get-message c 1873)                          ; delivery status
(sms/list-messages c {:status "failed" :limit 20})
(sms/list-received-sms c 12 {:limit 10})          ; incoming SMS
(sms/list-devices c)
(sms/get-account c)
(sms/create-webhook c "https://example.com/sms" ["sms.received" "sms.failed"])

;; In your webhook handler
(sms/verify-webhook raw-body (get headers "x-smsgora-signature") secret)

;; Anything else in the API
(sms/request c :get "/batches/311")
```

Errors are thrown as `ex-info` with `{:status 401}` (or 404, 429 ...) in `ex-data`.

## More SMS Gora integrations

[Java/Kotlin](https://github.com/TekservixHQ/smsgora-java) · [npm](https://www.npmjs.com/package/smsgora) · [PyPI](https://pypi.org/project/smsgora/) · [All docs](https://tekservix.com/smsgora/docs/)

support@tekservix.com · MIT licensed.
