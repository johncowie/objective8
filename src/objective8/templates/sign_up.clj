(ns objective8.templates.sign-up 
  (:require [net.cgrand.enlive-html :as html]
            [net.cgrand.jsoup :as jsoup]
            [ring.util.anti-forgery :refer [anti-forgery-field]]  
            [objective8.templates.page-furniture :as f]
            [objective8.templates.template-functions :as tf]))

(def sign-up-template (html/html-resource "templates/jade/sign-up.html" {:parser jsoup/parser}))

(defn apply-validations [{:keys [doc] :as context} nodes]
  (let [validation-data (get-in doc [:flash :validation])
        validation-report (:report validation-data)
        previous-inputs (:data validation-data)]
    (html/at nodes
             [:.clj-username-invalid-error] (when (contains? (:username validation-report) :invalid) identity)
             [:.clj-username-duplicated-error] (when (contains? (:username validation-report) :duplicated) identity)
             [:.clj-input-username] (html/set-attr :value (:username previous-inputs))

             [:.clj-email-empty-error] (when (contains? (:email-address validation-report) :empty) identity)
             [:.clj-email-invalid-error] (when (contains? (:email-address validation-report) :invalid) identity)
             [:.clj-input-email-address] (html/set-attr :value (:email-address previous-inputs)))))

(defn sign-up-page [{:keys [translations data doc] :as context}]
  (let [objective (:objective data)]
    (->> (html/at sign-up-template 
                  [:title] (html/content (:title doc))
                  [(and (html/has :meta) (html/attr= :name "description"))] (html/set-attr "content" (:description doc))
                  [:.clj-masthead-signed-out] (html/substitute (f/masthead context))
                  [:.clj-status-bar] (html/substitute (f/status-flash-bar context))

                  [:.clj-sign-up-form] (html/prepend (html/html-snippet (anti-forgery-field)))
                  [:.clj-username-error] (when-let [error-type (get-in doc [:errors :username])]
                                           (html/content (translations (keyword "sign-up" (name error-type))))))
         (apply-validations context)
         f/add-google-analytics
         (tf/translate context)
         html/emit*
         (apply str))))
