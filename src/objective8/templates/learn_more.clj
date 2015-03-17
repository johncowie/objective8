(ns objective8.templates.learn-more
  (:require [net.cgrand.enlive-html :as html]
            [objective8.templates.page-furniture :as f])) 

(def learn-more (html/html-resource "templates/jade/learn-more.html"))

(defn learn-more-page [context]
  (let [user (:user context)]
    (apply str
           (html/emit*
             (html/at learn-more
                      [:.clj-user-navigation-signed-out] (if user
                                                           (html/substitute (f/user-navigation-signed-in context))
                                                           (html/substitute (f/user-navigation-signed-out context))))))))

