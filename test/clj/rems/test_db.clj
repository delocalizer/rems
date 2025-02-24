(ns ^:integration rems.test-db
  "Namespace for tests that use an actual database."
  (:require [clojure.test :refer :all]
            [rems.config]
            [rems.db.applications]
            [rems.db.catalogue]
            [rems.db.core :as db]
            [rems.db.roles]
            [rems.db.users]
            [rems.service.test-data :as test-data]
            [rems.db.test-data-helpers :as test-helpers]
            [rems.db.testing :refer [test-db-fixture rollback-db-fixture]]))

(use-fixtures :once test-db-fixture)
(use-fixtures :each rollback-db-fixture)

(deftest test-get-catalogue-items
  (testing "without catalogue items"
    (is (empty? (rems.db.catalogue/get-catalogue-items))))

  (testing "with two items"
    (let [item1 (test-helpers/create-catalogue-item! {})
          item2 (test-helpers/create-catalogue-item! {})]
      (is (= (set [item1 item2]) (set (map :id (rems.db.catalogue/get-catalogue-items))))
          "should find the two items")
      (is (= item1 (:id (rems.db.catalogue/get-catalogue-item item1)))
          "should find same catalogue item by id")
      (is (= item2 (:id (rems.db.catalogue/get-catalogue-item item2)))
          "should find same catalogue item by id"))))

(deftest test-multi-applications
  (test-helpers/create-user! {:userid "test-user" :email "test-user@test.com" :name "Test-user"})
  (test-helpers/create-user! {:userid "handler" :email "handler@test.com" :name "Handler"})
  (let [applicant "test-user"
        wfid (test-helpers/create-workflow! {:handlers ["handler"]})
        res1 (test-helpers/create-resource! {:resource-ext-id "resid111"})
        res2 (test-helpers/create-resource! {:resource-ext-id "resid222"})
        form-id (test-helpers/create-form! {})
        item1 (test-helpers/create-catalogue-item! {:form-id form-id :resource-id res1 :workflow-id wfid})
        item2 (test-helpers/create-catalogue-item! {:form-id form-id :resource-id res2 :workflow-id wfid})
        app-id (test-helpers/create-application! {:catalogue-item-ids [item1 item2]
                                                  :actor applicant})]
    (test-helpers/command! {:type :application.command/submit
                            :application-id app-id
                            :actor applicant})
    (test-helpers/command! {:type :application.command/approve
                            :application-id app-id
                            :actor "handler"
                            :comment ""})
    (is (= :application.state/approved (:application/state (rems.db.applications/get-application-for-user applicant app-id))))

    (is (= ["resid111" "resid222"] (sort (map :resid (db/get-entitlements {:application app-id}))))
        "should create entitlements for both resources")))

(deftest test-roles
  (rems.db.users/add-user! "pekka" {})
  (rems.db.users/add-user! "simo" {})
  (rems.db.roles/add-role! "pekka" :owner)
  (rems.db.roles/add-role! "pekka" :owner) ; add should be idempotent
  (is (= #{:logged-in :owner} (rems.db.roles/get-roles "pekka")))
  (is (= #{:logged-in} (rems.db.roles/get-roles "simo")))
  (is (= #{:logged-in} (rems.db.roles/get-roles "juho"))) ; default role
  (is (thrown? RuntimeException (rems.db.roles/add-role! "pekka" :unknown-role))))

(deftest test-create-demo-data!
  ;; just a smoke test, check that create-demo-data doesn't fail
  (test-data/create-demo-data!)
  (is true))
