(ns objective8.back-end.domain.comments
  (:require [objective8.back-end.storage.storage :as storage]
            [objective8.utils :as utils]))

(defn replace-comment-on-id [comment comment-on-uri]
  (-> comment
      (assoc :comment-on-uri comment-on-uri)
      (dissoc :comment-on-id)))

(defn uri-for-comment [comment]
  (str "/comments/" (:_id comment)))

(defn store-comment-for! [entity-to-comment-on
                          {:keys [comment-on-uri] :as comment-data}]
  (when-let [{:keys [objective-id _id global-id entity]} entity-to-comment-on]
    (some-> comment-data
            (utils/select-all-or-nothing [:comment :created-by-id])
            (assoc :entity :comment
                   :comment-on-id global-id
                   :objective-id (if (= entity :objective) 
                                   _id
                                   objective-id))
            (dissoc :comment-on-uri)
            storage/pg-store!
            (dissoc :global-id)
            (utils/update-in-self [:uri] uri-for-comment)
            (replace-comment-on-id comment-on-uri))))

(def default-comment-query
  {:sorted-by :created-at
   :filter-type :none
   :limit 50})

(defn get-comments [entity-uri query-params]
  (when-let [{:keys [global-id]} (storage/pg-retrieve-entity-by-uri entity-uri :with-global-id)]
    (let [query (-> default-comment-query
                    (merge query-params)
                    (assoc :global-id global-id))]
      (->> (storage/pg-retrieve-comments-with-votes query)
           (map #(dissoc % :global-id))
           (map #(utils/update-in-self % [:uri] uri-for-comment))
           (map #(replace-comment-on-id % entity-uri))))))

(defn store-reason! [reason-data]
 (some-> reason-data
         (utils/select-all-or-nothing [:comment-id :reason])
         (assoc :entity :reason)
         storage/pg-store!))