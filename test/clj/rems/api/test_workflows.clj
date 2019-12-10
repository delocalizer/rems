(ns ^:integration rems.api.test-workflows
  (:require [clojure.test :refer :all]
            [rems.api.services.licenses :as licenses]
            [rems.api.services.workflow :as workflow]
            [rems.api.testing :refer :all]
            [rems.common-util :refer [index-by]]
            [rems.db.core :as db]
            [rems.db.test-data :as test-data]
            [rems.db.testing :refer [sync-with-database-time]]
            [rems.handler :refer [handler]]
            [ring.mock.request :refer :all]))

(use-fixtures
  :once
  api-fixture)

;; this is a subset of what we expect to get from the api
(def ^:private expected
  {:organization "abc"
   :title "workflow title"
   :workflow {:type "workflow/dynamic"
              :handlers [{:userid "handler" :email "handler@example.com" :name "Hannah Handler"}
                         {:userid "carl" :email "carl@example.com" :name "Carl Reviewer"}]}
   :enabled true
   :archived false})

(defn- fetch [api-key user-id wfid]
  (-> (request :get (str "/api/workflows/" wfid))
      (authenticate api-key user-id)
      handler
      read-ok-body
      (select-keys (keys expected))))

(deftest workflows-api-test
  (testing "list"
    (let [data (-> (request :get "/api/workflows")
                   (authenticate "42" "owner")
                   handler
                   assert-response-is-ok
                   read-body)]
      (is (coll-is-not-empty? data))))

  (let [id (test-data/create-workflow! {})]
    (testing "get by id"
      (let [data (-> (request :get (str "/api/workflows/" id))
                     (authenticate "42" "owner")
                     handler
                     assert-response-is-ok
                     read-body)]
        (is (= id (:id data)))))

    (testing "id not found"
      (let [response (-> (request :get (str "/api/workflows/" 666))
                         (authenticate "42" "owner")
                         handler)]
        (is (response-is-not-found? response)))))

  (testing "create dynamic workflow"
    (let [body (-> (request :post "/api/workflows/create")
                   (json-body {:organization "abc"
                               :title "workflow title"
                               :type :workflow/dynamic
                               :handlers ["handler" "carl"]})
                   (authenticate "42" "owner")
                   handler
                   assert-response-is-ok
                   read-body)
          id (:id body)]
      (is (< 0 id))
      (sync-with-database-time)
      (testing "and fetch"
        (is (= expected
               (fetch "42" "owner" id))))))

  (testing "create bureaucratic workflow"
    (let [body (-> (request :post "/api/workflows/create")
                   (json-body {:organization "abc"
                               :title "workflow title"
                               :type :workflow/bureaucratic
                               :handlers ["handler" "carl"]})
                   (authenticate "42" "owner")
                   handler
                   assert-response-is-ok
                   read-body)
          id (:id body)]
      (is (< 0 id))
      (sync-with-database-time)
      (testing "and fetch"
        (is (= (assoc-in expected [:workflow :type] "workflow/bureaucratic")
               (fetch "42" "owner" id)))))))

(deftest workflows-enabled-archived-test
  (let [api-key "42"
        user-id "owner"
        wfid (test-data/create-workflow! {:organization "abc"
                                          :title "workflow title"
                                          :handlers ["handler" "carl"]})
        lic-id (test-data/create-license! {})
        _ (db/create-workflow-license! {:wfid wfid :licid lic-id})

        fetch #(fetch api-key user-id wfid)
        archive-license! #(licenses/set-license-archived! {:id lic-id
                                                           :archived %})
        set-enabled! #(-> (request :put "/api/workflows/enabled")
                          (json-body {:id wfid
                                      :enabled %})
                          (authenticate api-key user-id)
                          handler
                          read-ok-body)
        set-archived! #(-> (request :put "/api/workflows/archived")
                           (json-body {:id wfid
                                       :archived %})
                           (authenticate api-key user-id)
                           handler
                           read-ok-body)]
    (sync-with-database-time)
    (testing "before changes"
      (is (= expected (fetch))))
    (testing "disable and archive"
      (is (:success (set-enabled! false)))
      (is (:success (set-archived! true)))
      (is (= (assoc expected
                    :enabled false
                    :archived true)
             (fetch))))
    (testing "re-enable"
      (is (:success (set-enabled! true)))
      (is (= (assoc expected
                    :archived true)
             (fetch))))
    (testing "unarchive"
      (is (:success (set-archived! false)))
      (is (= expected
             (fetch))))
    (testing "cannot unarchive if license is archived"
      (set-archived! true)
      (archive-license! true)
      (is (not (:success (set-archived! false))))
      (archive-license! false)
      (is (:success (set-archived! false))))))

(deftest workflows-edit-test
  (let [api-key "42"
        user-id "owner"
        wfid (test-data/create-workflow! {:organization "abc"
                                          :title "workflow title"
                                          :handlers ["handler" "carl"]})
        fetch #(fetch api-key user-id wfid)
        edit! #(-> (request :put "/api/workflows/edit")
                   (json-body (merge {:id wfid} %))
                   (authenticate api-key user-id)
                   handler
                   read-ok-body)]
    (sync-with-database-time)
    (testing "change title"
      (is (:success (edit! {:title "x"})))
      (is (= (assoc expected
                    :title "x")
             (fetch))))
    (testing "change handlers"
      (is (:success (edit! {:handlers ["owner" "alice"]})))
      (is (= (assoc expected
                    :title "x"
                    :workflow {:type "workflow/dynamic"
                               :handlers [{:email "owner@example.com"
                                           :name "Owner"
                                           :userid "owner"}
                                          {:email "alice@example.com"
                                           :name "Alice Applicant"
                                           :userid "alice"}]})
             (fetch))))))

(deftest workflows-api-filtering-test
  (let [enabled-wf (test-data/create-workflow! {})
        disabled-wf (test-data/create-workflow! {})
        _ (workflow/set-workflow-enabled! {:id disabled-wf
                                           :enabled false})
        enabled-and-disabled-wfs (set (map :id (-> (request :get "/api/workflows" {:disabled true})
                                                   (authenticate "42" "owner")
                                                   handler
                                                   assert-response-is-ok
                                                   read-body)))
        enabled-wfs (set (map :id (-> (request :get "/api/workflows")
                                      (authenticate "42" "owner")
                                      handler
                                      assert-response-is-ok
                                      read-body)))]
    (is (contains? enabled-and-disabled-wfs enabled-wf))
    (is (contains? enabled-and-disabled-wfs disabled-wf))
    (is (contains? enabled-wfs enabled-wf))
    (is (not (contains? enabled-wfs disabled-wf)))))

(deftest workflows-api-security-test
  (testing "without authentication"
    (testing "list"
      (let [response (-> (request :get (str "/api/workflows"))
                         handler)]
        (is (response-is-unauthorized? response))
        (is (= "unauthorized" (read-body response)))))
    (testing "create"
      (let [response (-> (request :post (str "/api/workflows/create"))
                         (json-body {:organization "abc"
                                     :title "workflow title"
                                     :type :rounds
                                     :rounds [{:type :approval
                                               :actors ["handler"]}]})
                         handler)]
        (is (response-is-unauthorized? response))
        (is (= "Invalid anti-forgery token" (read-body response))))))

  (testing "without owner role"
    (testing "list"
      (let [response (-> (request :get (str "/api/workflows"))
                         (authenticate "42" "alice")
                         handler)]
        (is (response-is-forbidden? response))
        (is (= "forbidden" (read-body response)))))
    (testing "create"
      (let [response (-> (request :post (str "/api/workflows/create"))
                         (json-body {:organization "abc"
                                     :title "workflow title"
                                     :type :rounds
                                     :rounds [{:type :approval
                                               :actors ["handler"]}]})
                         (authenticate "42" "alice")
                         handler)]
        (is (response-is-forbidden? response))
        (is (= "forbidden" (read-body response)))))))
