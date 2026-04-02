(defproject registration-service "0.1.0-SNAPSHOT"
  :description "Independent Registration Service for Vozni Park Fleet Management System"
  :url "https://github.com/Vukella/vozni-park-registration-service"
  :license {:name "MIT"}

  :dependencies [[org.clojure/clojure "1.12.0"]

                 ;; HTTP Server
                 [ring/ring-core "1.13.0"]
                 [ring/ring-jetty-adapter "1.13.0"]
                 [ring/ring-json "0.5.1"]

                 ;; Routing
                 [metosin/reitit-ring "0.7.2"]
                 [metosin/reitit-middleware "0.7.2"]

                 ;; Security — JWT, Auth, Password Hashing
                 [buddy/buddy-sign "3.6.1-359"]
                 [buddy/buddy-auth "3.0.323"]
                 [buddy/buddy-hashers "2.0.167"]
                 [org.mindrot/jbcrypt "0.4"]

                 ;; Database
                 [com.github.seancorfield/next.jdbc "1.3.939"]
                 [com.mysql/mysql-connector-j "8.3.0"]
                 [com.zaxxer/HikariCP "5.1.0"]

                 ;; Email
                 [com.draines/postal "2.0.5"]

                 ;; HTML Templating
                 [selmer "1.12.61"]

                 ;; Logging
                 [ch.qos.logback/logback-classic "1.4.14"]
                 [org.clojure/tools.logging "1.3.0"]

                 ;; JSON
                 [cheshire "5.13.0"]]

  :main ^:skip-aot registration-service.core
  :target-path "target/%s"

  :profiles {:dev {:resource-paths ["resources" "dev-resources"]}

             :uberjar {:aot :all
                       :jvm-opts ["-Dclojure.compiler.direct-linking=true"]}})