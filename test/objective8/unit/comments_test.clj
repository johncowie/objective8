(ns objective8.unit.comments-test
  (:require [midje.sweet :refer :all]
            [objective8.comments :as comments]
            [objective8.objectives :as objectives]
            [objective8.storage.storage :as storage]))

(def USER_ID 1)
(def OBJECTIVE_ID 234)
(def GLOBAL_ID 345)

(def comment {:objective-id OBJECTIVE_ID
              :comment-on-id GLOBAL_ID})

(fact "A comment can be created when the associated objective is not in drafting"
      (comments/create-comment-on-objective! comment) => :stored-comment
      (provided
       (objectives/retrieve-objective OBJECTIVE_ID) => {:drafting-started false}
       (storage/pg-store! anything) => :stored-comment))

(fact "Attempting to create a comment against an objective that is in drafting returns nil"
      (comments/create-comment-on-objective! comment) => nil
      (provided
        (objectives/retrieve-objective OBJECTIVE_ID) => {:drafting-started true}))


(fact "By default, only the first 50 comments are retrieved"
      (comments/retrieve-comments GLOBAL_ID) => anything
      (provided
       (storage/pg-retrieve {:entity :comment
                             :comment-on-id GLOBAL_ID}
                            {:limit 50}) => []))

(fact "Postgresql exceptions are not caught"
      (comments/store-comment! {:comment "something"
                                :comment-on-uri "/some/uri"
                                :created-by-id 1}) => (throws org.postgresql.util.PSQLException)
      (provided
        (storage/pg-store! anything) =throws=> (org.postgresql.util.PSQLException.
                                                (org.postgresql.util.ServerErrorMessage. "" 0))
        (storage/pg-retrieve-entity-by-uri "/some/uri") => {}))
