(ns rems.paging
  (:require [reagent.core :as r]
            [rems.atoms :as atoms]
            [rems.guide-util :refer [component-info example]]
            [rems.text :refer [text]]))

(defn- page-number [paging page on-change]
  (if (= (:current-page paging) page)
    [:span (str (inc page))]

    [atoms/link {:href ""
                 :label (str (inc page))
                 :on-click #(on-change (assoc paging :current-page page))}]))

(defn paging-field
  "Component for showing page numbers.

  Intended to be used together with a `rems.table/table` through `rems.table/paging`.

  `:id`                      - identity of the component (derived from table)
  `:on-change`               - callback
  `:paging`                  - paging state (with table)
    `:current-page`          - the current page (0-indexed)
    `:show-all-page-numbers` - state of whether to show all page numbers or `...`
  `:pages`                   - how many pages exist"
  [{:keys [id on-change paging pages]}]
  (r/with-let [show-all-page-numbers (r/atom (:show-all-page-numbers paging))]
    (when (> pages 1)
      [:div.d-flex.gap-1.justify-content-center.flex-wrap.mr-3.my-3 {:id id}
       (text :t.table.paging/page)

       (if (or @show-all-page-numbers
               (< pages 10))
         ;; just show them all
         (for [page (range pages)]
           ^{:key (str id "-page-" page)}
           [page-number paging page on-change])

         ;; show 1 2 3 ... 7 8 9
         (let [first-pages (take 3 (range pages))
               last-pages (take-last 3 (drop 3 (range pages)))]
           [:<>
            (for [page first-pages]
              ^{:key (str id "-page-" page)}
              [page-number paging page on-change])

            ^{:key (str id "-page-...")}
            [atoms/link {:label "..."
                         :on-click #(reset! show-all-page-numbers true)}]

            (for [page last-pages]
              ^{:key (str id "-page-" page)}
              [page-number paging page on-change])]))])))


(defn guide []
  [:div
   (component-info paging-field)
   (example "no pages"
            [paging-field {:id "paging1"
                           :pages 0}])

   (example "1 page"
            [paging-field {:id "paging2"
                           :pages 1}])

   (example "3 pages, current page 2"
            [paging-field {:id "paging3"
                           :paging {:current-page 1}
                           :pages 3}])

   (example "9 pages, current page 2"
            [paging-field {:id "paging4"
                           :paging {:current-page 1}
                           :pages 9}])

   (example "100 pages, current page 2, not opened"
            [paging-field {:id "paging5"
                           :paging {:current-page 1}
                           :pages 100}])

   (example "100 pages, current page 2, opened all page numbers"
            [paging-field {:id "paging5"
                           :paging {:current-page 1
                                    :show-all-page-numbers true}
                           :pages 100}])])

