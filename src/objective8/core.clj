(ns objective8.core
  (:use [org.httpkit.server :only [run-server]])
  (:require [clojure.tools.logging :as log]
            [cemerick.friend :as friend]
            [ring.middleware.params :refer [wrap-params]]
            [ring.middleware.keyword-params :refer [wrap-keyword-params]]
            [ring.middleware.session :refer [wrap-session]]
            [ring.middleware.session.memory :refer [memory-store]]
            [ring.middleware.flash :refer [wrap-flash]]
            [ring.middleware.json :refer [wrap-json-params wrap-json-response]]
            [ring.middleware.x-headers :refer [wrap-xss-protection wrap-frame-options wrap-content-type-options]]
            [ring.middleware.ssl :refer [wrap-ssl-redirect wrap-hsts wrap-forwarded-scheme]]
            [bidi.ring :refer [make-handler]]
            [taoensso.tower.ring :refer [wrap-tower]]
            [objective8.routes :as routes]
            [objective8.config :as config]
            [objective8.utils :as utils]
            [objective8.middleware :as m]
            [objective8.front-end.permissions :as permissions]
            [objective8.front-end.translation :refer [configure-translations]]
            [objective8.front-end.workflows.twitter :refer [twitter-workflow configure-twitter]]
            [objective8.front-end.workflows.stub-twitter :refer [stub-twitter-workflow]]
            [objective8.front-end.workflows.sign-up :refer [sign-up-workflow]]
            [objective8.front-end.handlers :as front-end-handlers]
            [objective8.back-end.domain.users :as users]
            [objective8.back-end.handlers :as back-end-handlers]
            [objective8.back-end.storage.storage :as storage]
            [objective8.back-end.storage.database :as db]
            [objective8.back-end.domain.bearer-tokens :as bt])
 ; (:gen-class)
  )

(def handlers {;; Front End Handlers
               :fe/index front-end-handlers/index
               :fe/sign-in front-end-handlers/sign-in
               :fe/sign-out front-end-handlers/sign-out
               :fe/profile front-end-handlers/profile
               :fe/project-status front-end-handlers/project-status
               :fe/learn-more front-end-handlers/learn-more
               :fe/admin-activity front-end-handlers/admin-activity
               :fe/create-objective-form (friend/wrap-authorize (utils/anti-forgery-hook front-end-handlers/create-objective-form) #{:signed-in})
               :fe/create-objective-form-post (friend/wrap-authorize (utils/anti-forgery-hook front-end-handlers/create-objective-form-post) #{:signed-in})
               :fe/objective-list (utils/anti-forgery-hook front-end-handlers/objective-list) 
               :fe/objective (utils/anti-forgery-hook front-end-handlers/objective-detail)
               :fe/get-comments-for-objective (utils/anti-forgery-hook front-end-handlers/get-comments-for-objective)
               :fe/add-a-question (friend/wrap-authorize (utils/anti-forgery-hook front-end-handlers/add-a-question) #{:signed-in}) 
               :fe/add-question-form-post (friend/wrap-authorize (utils/anti-forgery-hook front-end-handlers/add-question-form-post) #{:signed-in})
               :fe/question-list (utils/anti-forgery-hook front-end-handlers/question-list)
               :fe/question (utils/anti-forgery-hook front-end-handlers/question-detail)
               :fe/add-answer-form-post (friend/wrap-authorize (utils/anti-forgery-hook front-end-handlers/add-answer-form-post) #{:signed-in})
               :fe/writers-list (utils/anti-forgery-hook front-end-handlers/writers-list)
               :fe/invite-writer (m/wrap-authorise-writer-inviter (utils/anti-forgery-hook front-end-handlers/invite-writer)) 
               :fe/invitation-form-post (m/wrap-authorise-writer-inviter (utils/anti-forgery-hook front-end-handlers/invitation-form-post))
               :fe/writer-invitation front-end-handlers/writer-invitation
               :fe/create-profile-get (friend/wrap-authorize (utils/anti-forgery-hook front-end-handlers/create-profile-get) #{:signed-in})
               :fe/create-profile-post (friend/wrap-authorize (utils/anti-forgery-hook front-end-handlers/create-profile-post) #{:signed-in})

               :fe/edit-profile-get (friend/wrap-authorize (utils/anti-forgery-hook front-end-handlers/edit-profile-get) #{:signed-in})
               :fe/edit-profile-post (friend/wrap-authorize (utils/anti-forgery-hook front-end-handlers/edit-profile-post) #{:signed-in})
               :fe/accept-invitation (friend/wrap-authorize (utils/anti-forgery-hook front-end-handlers/accept-invitation) #{:signed-in})
               :fe/decline-invitation (utils/anti-forgery-hook front-end-handlers/decline-invitation) 
               :fe/add-draft-get (m/authorize-based-on-request (utils/anti-forgery-hook front-end-handlers/add-draft-get)
                                                               permissions/request->writer-roles)
               :fe/add-draft-post (m/authorize-based-on-request (utils/anti-forgery-hook front-end-handlers/add-draft-post)
                                                                permissions/request->writer-roles)
               :fe/draft (utils/anti-forgery-hook front-end-handlers/draft)
               :fe/get-comments-for-draft (utils/anti-forgery-hook front-end-handlers/get-comments-for-draft)
               :fe/draft-diff front-end-handlers/draft-diff
               :fe/draft-section (utils/anti-forgery-hook front-end-handlers/draft-section) 
               :fe/draft-list front-end-handlers/draft-list
               :fe/import-draft-get (m/authorize-based-on-request (utils/anti-forgery-hook front-end-handlers/import-draft-get)
                                                                  permissions/request->writer-roles)
               :fe/import-draft-post (m/authorize-based-on-request (utils/anti-forgery-hook front-end-handlers/import-draft-post)
                                                                   permissions/request->writer-roles)
               :fe/dashboard-questions (m/authorize-based-on-request (utils/anti-forgery-hook front-end-handlers/dashboard-questions) permissions/request->writer-roles)
               :fe/dashboard-comments (m/authorize-based-on-request (utils/anti-forgery-hook front-end-handlers/dashboard-comments) permissions/request->writer-roles)
               :fe/dashboard-annotations (m/authorize-based-on-request (utils/anti-forgery-hook front-end-handlers/dashboard-annotations) permissions/request->writer-roles)
               
               :fe/post-up-vote (friend/wrap-authorize (utils/anti-forgery-hook front-end-handlers/post-up-vote) #{:signed-in}) 
               :fe/post-down-vote (friend/wrap-authorize (utils/anti-forgery-hook front-end-handlers/post-down-vote) #{:signed-in}) 
               :fe/post-comment (friend/wrap-authorize (utils/anti-forgery-hook front-end-handlers/post-comment) #{:signed-in})
               :fe/post-annotation (friend/wrap-authorize (utils/anti-forgery-hook front-end-handlers/post-annotation) #{:signed-in})
               :fe/post-star (friend/wrap-authorize (utils/anti-forgery-hook front-end-handlers/post-star) #{:signed-in})
               :fe/post-mark (m/authorize-based-on-request (utils/anti-forgery-hook front-end-handlers/post-mark) permissions/mark-request->mark-question-roles)
               :fe/post-writer-note (friend/wrap-authorize (utils/anti-forgery-hook front-end-handlers/post-writer-note) #{:signed-in})
               :fe/admin-removal-confirmation-get (friend/wrap-authorize (utils/anti-forgery-hook front-end-handlers/admin-removal-confirmation) #{:admin})
               :fe/admin-removal-confirmation-post (friend/wrap-authorize (utils/anti-forgery-hook front-end-handlers/post-admin-removal-confirmation) #{:admin})
               :fe/post-admin-removal (friend/wrap-authorize (utils/anti-forgery-hook front-end-handlers/post-admin-removal) #{:admin})
               :fe/error-configuration front-end-handlers/error-configuration

               
               ;; API Handlers
               :api/post-user-profile (m/wrap-bearer-token back-end-handlers/post-user-profile bt/token-provider)
               :api/get-user-by-query (m/wrap-bearer-token back-end-handlers/find-user-by-query bt/token-provider)
               :api/get-user (m/wrap-bearer-token back-end-handlers/get-user bt/token-provider)
               :api/put-writer-profile (m/wrap-bearer-token back-end-handlers/put-writer-profile bt/token-provider)
               :api/post-objective (m/wrap-bearer-token back-end-handlers/post-objective bt/token-provider)
               :api/get-objective (m/wrap-bearer-token back-end-handlers/get-objective bt/token-provider) 
               :api/get-objectives (m/wrap-bearer-token back-end-handlers/get-objectives bt/token-provider) 
               :api/post-comment (m/wrap-bearer-token back-end-handlers/post-comment bt/token-provider)
               :api/get-comments back-end-handlers/get-comments
               :api/post-question (m/wrap-bearer-token back-end-handlers/post-question bt/token-provider)
               :api/get-question back-end-handlers/get-question
               :api/get-questions-for-objective back-end-handlers/retrieve-questions
               :api/get-answers-for-question back-end-handlers/get-answers
               :api/post-answer (m/wrap-bearer-token back-end-handlers/post-answer bt/token-provider)
               :api/post-invitation (m/wrap-bearer-token back-end-handlers/post-invitation bt/token-provider)
               :api/get-invitation back-end-handlers/get-invitation
               :api/post-writer (m/wrap-bearer-token back-end-handlers/post-writer bt/token-provider)
               :api/put-invitation-declination (m/wrap-bearer-token back-end-handlers/put-invitation-declination bt/token-provider)
               :api/get-writers-for-objective back-end-handlers/retrieve-writers
               :api/get-objectives-for-writer back-end-handlers/get-objectives-for-writer
               :api/post-draft (m/wrap-bearer-token back-end-handlers/post-draft bt/token-provider)
               :api/get-draft back-end-handlers/get-draft
               :api/get-drafts-for-objective back-end-handlers/retrieve-drafts
               :api/get-sections back-end-handlers/get-sections
               :api/get-section back-end-handlers/get-section
               :api/get-annotations back-end-handlers/get-annotations
               :api/post-admin-removal (m/wrap-bearer-token back-end-handlers/post-admin-removal bt/token-provider)
               :api/get-admin-removals back-end-handlers/get-admin-removals
               :api/post-up-down-vote (m/wrap-bearer-token back-end-handlers/post-up-down-vote bt/token-provider)
               :api/post-star (m/wrap-bearer-token back-end-handlers/post-star bt/token-provider)
               :api/post-mark (m/wrap-bearer-token back-end-handlers/post-mark bt/token-provider)
               :api/post-writer-note (m/wrap-bearer-token back-end-handlers/post-writer-note bt/token-provider)})
  
(defn app [app-config]
  (-> (make-handler routes/routes (some-fn handlers #(when (fn? %) %)))
      (m/wrap-not-found front-end-handlers/error-404)
      (friend/authenticate (:authentication app-config))
      (wrap-tower (:translation app-config))
      m/strip-trailing-slashes
      wrap-keyword-params
      wrap-params
      wrap-json-params
      wrap-json-response
      wrap-flash
      (wrap-session {:cookie-attrs {:http-only true}
                     :store (:session-store app-config)})
      (wrap-xss-protection true {:mode :block})
      (wrap-frame-options :sameorigin)
      ((if (:https app-config)
         (comp wrap-forwarded-scheme wrap-ssl-redirect)
         identity))))

(defonce server (atom nil))

(def app-config
  {:authentication {:allow-anon? true
                    :workflows [(if (= (config/get-var "FAKE_TWITTER_MODE") "TRUE")
                                  stub-twitter-workflow
                                  (twitter-workflow (configure-twitter))),
                                sign-up-workflow]
                    :login-uri "/sign-in"}
   :session-store (memory-store)
   :translation (configure-translations)
   :https (config/get-var "HTTPS_ONLY")
   :db-spec db/postgres-spec})

(defn get-bearer-token-details []
  (let [bearer-name (config/get-var "API_BEARER_NAME")
        bearer-token (config/get-var "API_BEARER_TOKEN")]
    (when (and bearer-name bearer-token)
      {:bearer-name bearer-name
       :bearer-token bearer-token})))

(defn store-admin [twitter-id]
  (when-not (users/get-admin-by-twitter-id twitter-id)
    (log/info "Storing an admin")
    (users/store-admin! {:twitter-id twitter-id})))

(defn initialise-api []
  (when-let [bearer-token-details (get-bearer-token-details)]
    (if (bt/get-token (bearer-token-details :bearer-name))
      (bt/update-token! bearer-token-details)
      (do 
        (log/info "Storing bearer token details")
        (bt/store-token! bearer-token-details))))
  (when-let [admins-var (config/get-var "ADMINS")]
    (let [admins (clojure.string/split admins-var #" ")]
        (doall (map store-admin admins)))))

(defn start-server 
  ([]
   (start-server app-config)) 
  ([app-config] 
   (let [port (Integer/parseInt (config/get-var "APP_PORT" "8080"))]
     (db/connect! (:db-spec app-config)) 
     (initialise-api)
     (log/info (str "Starting objective8 on port " port))
     (reset! server (run-server (app app-config) {:port port})))))

(defn -main []
  (start-server))

(defn stop-server []
  (when-not (nil? @server)
    (log/info "Stopping objective8")
    (@server)
    (reset! server nil)))

(defn restart-server []
  (stop-server)
  (start-server))
