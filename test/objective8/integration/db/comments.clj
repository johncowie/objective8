(ns objective8.integration.db.comments
  (:require [midje.sweet :refer :all]
            [objective8.back-end.domain.comments :as comments]
            [objective8.utils :as utils]
            [objective8.back-end.storage.actions :as actions]
            [objective8.integration.integration-helpers :as ih]
            [objective8.integration.storage-helpers :as sh]))

(facts "about storing comments"
       (against-background
        [(before :contents (do (ih/db-connection)
                               (ih/truncate-tables)))
         (after :facts (ih/truncate-tables))]

        (fact "comments can be stored against a draft"
              (let [{user-id :_id :as user} (sh/store-a-user)
                    {o-id :objective-id d-id :_id :as draft} (sh/store-a-draft)
                    uri-for-draft (str "/objectives/" o-id "/drafts/" d-id)
                    comment-data {:comment-on-uri uri-for-draft
                                  :comment "A comment"
                                  :created-by-id user-id}]
                (comments/store-comment-for! draft comment-data) => (contains {:_id integer?
                                                                               :uri (contains "/comments/")
                                                                               :comment-on-uri uri-for-draft
                                                                               :comment "A comment"
                                                                               :created-by-id user-id})
                (comments/store-comment-for! draft comment-data) =not=> (contains {:comment-on-id anything})
                (comments/store-comment-for! draft comment-data) =not=> (contains {:global-id anything})))

        (fact "comments can be stored against an objective"
              (let [{user-id :_id :as user} (sh/store-a-user)
                    {o-id :_id :as objective} (sh/store-an-open-objective)
                    uri-for-objective (str "/objectives/" o-id)
                    comment-data {:comment-on-uri uri-for-objective
                                  :comment "A comment"
                                  :created-by-id user-id}]
                (comments/store-comment-for! objective comment-data) => (contains {:_id integer?
                                                                                   :comment-on-uri uri-for-objective
                                                                                   :comment "A comment"
                                                                                   :created-by-id user-id})
                (comments/store-comment-for! objective comment-data) =not=> (contains {:comment-on-id anything})))))

(facts "about storing reasons"
       (against-background 
        [(before :contents (do (ih/db-connection)
                               (ih/truncate-tables)))
         (after :facts (ih/truncate-tables))]) 

       (fact "reasons can be stored against an annotation comment"
             (let [{comment-id :_id} (sh/store-a-comment)
                   reason-data {:comment-id comment-id
                                 :reason "unclear"}]
             (comments/store-reason! reason-data) => (contains {:_id integer?
                                                                :comment-id comment-id
                                                                :reason "unclear"}))))

(facts "about getting comments by uri"
       (against-background
        [(before :contents (do (ih/db-connection)
                               (ih/truncate-tables)))
         (after :facts (ih/truncate-tables))]

        (fact "gets the comments in the requested order"
              (let [objective (sh/store-an-open-objective)
                    objective-uri (str "/objectives/" (:_id objective))

                    {first-comment-id :_id} (-> (sh/store-a-comment {:entity objective}) (sh/with-votes {:up 2 :down 1}))
                    {second-comment-id :_id} (sh/store-a-comment {:entity objective})
                    {third-comment-id :_id} (-> (sh/store-a-comment {:entity objective}) (sh/with-votes {:up 1 :down 2}))]
                (comments/get-comments objective-uri {:sorted-by :created-at :filter-type :none}) => (contains [(contains {:_id third-comment-id})
                                                                                           (contains {:_id second-comment-id})
                                                                                           (contains {:_id first-comment-id})])

                (comments/get-comments objective-uri {:sorted-by :up-votes :filter-type :none})=> (contains [(contains {:_id first-comment-id})
                                                                                         (contains {:_id third-comment-id})
                                                                                         (contains {:_id second-comment-id})])

                (comments/get-comments objective-uri {:sorted-by :down-votes :filter-type :none})=> (contains [(contains {:_id third-comment-id})
                                                                                           (contains {:_id first-comment-id})
                                                                                           (contains {:_id second-comment-id})])))


      (fact "filters comments according to filter type"
            (let [objective (sh/store-an-open-objective)
                  objective-uri (str "/objectives/" (:_id objective))
                  {comment-without-note-id :_id} (sh/store-a-comment {:entity objective :comment-text "without note"})
                  {comment-with-note-id :_id} (-> (sh/store-a-comment {:entity objective :comment-text "with note"})
                                                  sh/with-note)]
             (comments/get-comments objective-uri {:filter-type :has-writer-note}) => (just [(contains {:_id comment-with-note-id})])
             (comments/get-comments objective-uri {:filter-type :none}) => (contains [(contains {:_id comment-with-note-id})
                                                                                      (contains {:_id comment-without-note-id})]
                                                                                     :in-any-order)))

        (tabular
         (fact "gets comments with aggregate votes"
               (let [objective (sh/store-an-open-objective)
                     objective-uri (str "/objectives/" (:_id objective))

                     comment (-> (sh/store-a-comment {:entity objective}) (sh/with-votes {:up 2 :down 10}))]
                 (-> (comments/get-comments objective-uri {:sorted-by ?sorted-by :filter-type :none})
                     first) => (contains {:votes {:up 2 :down 10}})))
         ?sorted-by :up-votes :down-votes :created-at)

         (def REASON "unclear")
         (def hiccup '(["h1" {:data-section-label "1234abcd"} "A Heading"] ["p" {:data-section-label "abcd1234"} "A paragraph"]))

         (fact "get comments with reasons if they exist"
               (let [{user-id :_id :as user} (sh/store-a-user)
                     {draft-id :_id objective-id :objective-id} (sh/store-a-draft {:content hiccup})
                     section-uri (utils/local-path-for :fe/draft-section
                                                 :id objective-id
                                                 :d-id draft-id
                                                 :section-label "1234abcd")

                     comment-data {:comment-on-uri section-uri
                                   :reason REASON
                                   :comment "test comment"
                                   :created-by-id user-id}

                     comment (actions/create-comment! comment-data)]
                 (-> (comments/get-comments section-uri {:sorted-by :created-at :filter-type :none})
                     first)  => (contains {:reason REASON})))

        (tabular
         (fact "gets comments with user name"
               (let [objective (sh/store-an-open-objective)
                     objective-uri (str "/objectives/" (:_id objective))

                     user (sh/store-a-user)

                     comment (sh/store-a-comment {:user user :entity objective})]
                 (-> (comments/get-comments objective-uri {:sorted-by ?sorted-by :filter-type :none})
                     first) => (contains {:username (:username user)})))
         ?sorted-by :up-votes :down-votes :created-at)

        (tabular
         (fact "gets comments with uris rather than global ids"
               (let [objective (sh/store-an-open-objective)
                     objective-uri (str "/objectives/" (:_id objective))

                     comment (sh/store-a-comment {:entity objective})
                     comment-uri (str "/comments/" (:_id comment))]
                 (-> (comments/get-comments objective-uri {:sorted-by ?sorted-by :filter-type :none})
                     first) =not=> (contains {:comment-on-id anything})    
                 (-> (comments/get-comments objective-uri {:sorted-by ?sorted-by :filter-type :none})
                     first) =not=> (contains {:global-id anything})
                 (-> (comments/get-comments objective-uri {:sorted-by ?sorted-by :filter-type :none})
                     first) => (contains {:uri comment-uri
                                                                                                    :comment-on-uri objective-uri})))
         ?sorted-by :up-votes :down-votes :created-at)))
