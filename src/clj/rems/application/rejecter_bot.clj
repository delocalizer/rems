(ns rems.application.rejecter-bot
  (:require [clj-time.core :as time]
            [clojure.tools.logging :as log]
            [rems.common.application-util :as application-util]
            [rems.db.applications]
            [rems.permissions :as permissions]))

(def bot-userid "rejecter-bot")

(defn- should-reject? [application]
  (not (empty? (:application/blacklist application))))

(defn- can-reject? [application]
  (contains? (permissions/user-permissions application bot-userid) :application.command/reject))

(defn- consider-rejecting [application]
  (when (and (application-util/is-handler? application bot-userid)
             (should-reject? application)
             (can-reject? application))
    (log/info "Rejecter bot rejecting application" (:application/id application) "based on blacklist" (:application/blacklist application))
    [{:type :application.command/reject
      :application-id (:application/id application)
      :time (time/now)
      :comment ""
      :actor bot-userid}]))

(defn reject-all-applications-by
  "Go through all applications by the given user-ids and reject any if necessary. Returns sequence of commands."
  [& user-ids]
  (->> user-ids
       (eduction (mapcat rems.db.applications/get-my-applications-full)
                 (map :application/id)
                 (distinct)
                 (map rems.db.applications/get-application)
                 (mapcat consider-rejecting))
       (into [])))

(defn run-rejecter-bot [new-events]
  (let [by-type (group-by :event/type new-events)
        submissions (get by-type :application.event/submitted)
        submitted-applications (mapv #(rems.db.applications/get-application (:application/id %)) submissions)
        revokes (get by-type :application.event/revoked)
        revoked-users (->> revokes
                           (map (comp rems.db.applications/get-application :application/id))
                           (mapcat application-util/applicant-and-members)
                           (map :userid))]
    (doall
     (concat
      (mapcat consider-rejecting submitted-applications)
      (apply reject-all-applications-by revoked-users)))))
