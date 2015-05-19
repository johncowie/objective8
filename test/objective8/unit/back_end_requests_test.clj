(ns objective8.unit.back-end-requests-test
  (:require [midje.sweet :refer :all]
            [objective8.back-end.requests :as br]))

(def OBJECTIVE_ID 1)
(def QUESTION_ID 2)

(def QUESTION_URI (str "/objectives/" OBJECTIVE_ID "/questions/" QUESTION_ID))
(def OBJECTIVE_URI (str "/objectives/" OBJECTIVE_ID))

(facts "request->answers-query"
       (tabular
        (fact "returns a query map when the request is valid"
              (br/request->answers-query {:params ?params
                                          :route-params {:id OBJECTIVE_ID :q-id QUESTION_ID}})
              => ?answers-query)
        ?params                          ?answers-query
        {:sorted-by "created-at"}        {:sorted-by :created-at :filter-type :none :question-uri QUESTION_URI}
        {:sorted-by "up-votes"}          {:sorted-by :up-votes   :filter-type :none :question-uri QUESTION_URI}
        {:sorted-by "down-votes"}        {:sorted-by :down-votes :filter-type :none :question-uri QUESTION_URI}
        {:filter-type "has-writer-note"} {:sorted-by :created-at :filter-type :has-writer-note :question-uri QUESTION_URI}
        {:filter-type "has-writer-note"
         :sorted-by "up-votes"}          {:sorted-by :up-votes :filter-type :has-writer-note :question-uri QUESTION_URI})

       (tabular
        (fact "returns nil when the request is invalid"
              (br/request->answers-query {:params ?params
                                          :route-params {:id OBJECTIVE_ID :q-id QUESTION_ID}})
              => nil)
        ?params
        {:sorted-by "invalid"}
        {:filter-type "invalid"}
        {:sorted-by "invalid" :filter-type "invalid"}))

(facts "request->comments-query"
       (tabular
         (fact "returns a query map when the request is valid"
               (let [request {:params (assoc ?params :uri OBJECTIVE_URI)}]
                 (br/request->comments-query request)) => ?comments-query)

         ?params                           ?comments-query
         {}                                {:uri OBJECTIVE_URI}
         {:sorted-by "created-at"}         {:uri OBJECTIVE_URI :sorted-by :created-at}
         {:sorted-by "up-votes"}           {:uri OBJECTIVE_URI :sorted-by :up-votes}
         {:sorted-by "down-votes"}         {:uri OBJECTIVE_URI :sorted-by :down-votes}
         {:filter-type "has-writer-note"}  {:uri OBJECTIVE_URI :filter-type :has-writer-note}
         {:filter-type "none"}             {:uri OBJECTIVE_URI :filter-type :none}
         {:limit "10"}                     {:uri OBJECTIVE_URI :limit 10}
         {:sorted-by "created-at"
          :limit "10"
          :filter-type "has-writer-note"}  {:uri OBJECTIVE_URI :filter-type :has-writer-note :sorted-by :created-at :limit 10})
       
       (fact "returns nil when the uri is missing from the request parameters"
             (br/request->comments-query {:params {}}) => nil)

       (tabular
         (fact "returns nil when the request parameters are invalid"
               (let [request {:params (assoc ?params :uri OBJECTIVE_URI)}]
                 (br/request->comments-query request)) => nil)

         ?params
         {:sorted-by "not-valid"}
         {:filter-type "not-valid"}
         {:limit "not-valid"}
         {:limit "-10"}))