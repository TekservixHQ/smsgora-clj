(defproject com.tekservix/smsgora-clj "1.0.0"
  :description "SMS Gora SMS Gateway: Clojure client by Tekservix. Send and receive SMS through your own Android phones, with delivery reports and signed webhooks."
  :url "https://tekservix.com/smsgora"
  :license {:name "MIT" :url "https://opensource.org/licenses/MIT"}
  :scm {:name "git" :url "https://github.com/TekservixHQ/smsgora-clj"}
  :pom-addition [:developers [:developer [:name "Tekservix"] [:email "support@tekservix.com"] [:url "https://tekservix.com"]]]
  :dependencies [[org.clojure/clojure "1.11.4" :scope "provided"]
                 [com.tekservix/smsgora "1.0.4"]]
  :global-vars {*warn-on-reflection* true}
  :deploy-repositories [["clojars" {:url "https://repo.clojars.org"
                                    :username :env/clojars_username
                                    :password :env/clojars_password
                                    :sign-releases false}]])
