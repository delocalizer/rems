(ns rems.layout
  (:require [clojure.string :as str]
            [hiccup.page :refer [html5 include-css include-js]]
            [rems.service.public]
            [rems.common.git :as git]
            [rems.config :refer [env]]
            [rems.context :as context]
            [rems.service.organizations]
            [cognitect.transit]
            [rems.text :refer [text with-language]]
            [ring.middleware.anti-forgery :refer [*anti-forgery-token*]]
            [ring.util.http-response :as response]))

(defn- css-filename [language]
  (str "/css/" (name language) "/screen.css"))

(defn- cache-bust [filename]
  (str filename "?" (or (:revision git/+version+)
                        ;; cache busting not strictly needed for dev mode, see rems.handler/dev-js-handler
                        (System/currentTimeMillis))))

;; TODO: consider refactoring together with style utils
(defn- resolve-image [path]
  (when path
    (if (str/starts-with? path "http")
      path
      (str (get-in env [:theme :img-path]) path))))

(defn- theme-get [& attrs]
  (when (seq attrs)
    (if-some [v (get-in env [:theme (first attrs)])]
      v
      (recur (rest attrs)))))

(defn- logo-preloads
  "Preload important images so that the paint can happen earlier."
  []
  (for [href (->>
              ;; localized logos or fallbacks
              (let [lang-key (some-> (if (bound? #'context/*lang*)
                                       context/*lang*
                                       (env :default-language))
                                     name)]
                [(theme-get (keyword (str "logo-name-" lang-key)) :logo-name)
                 (theme-get (keyword (str "logo-name-sm-" lang-key)) :logo-name-sm)
                 (theme-get (keyword (str "navbar-logo-name-" lang-key)) :navbar-logo-name)])

              distinct
              (map resolve-image)
              (remove nil?))]
    [:link {:rel "preload" :as "image" :href href :type "image/png"}]))

(defn- inline-value [setter value]
  (let [os (java.io.ByteArrayOutputStream. 4096)
        _ (cognitect.transit/write (cognitect.transit/writer os :json) value)
        transit-value (.toString os "UTF-8")]
    [:script {:type "text/javascript"}
     (format "%s(%s);"
             setter
             (pr-str transit-value))]))

(defn- page-template
  [content & [app-content]]
  (let [lang (if (bound? #'context/*lang*)
               context/*lang*
               (env :default-language))]
    (with-language lang
      (html5 [:html {:lang "en"}
              (into [:head
                     [:meta {:http-equiv "Content-Type" :content "text/html; charset=UTF-8"}]
                     [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
                     [:meta {:name "description" :content (text :t.meta/description)}]
                     [:meta {:name "keywords" :content (text :t.meta/keywords)}]
                     [:link {:rel "icon" :href "/img/favicon.ico" :type "image/x-icon"}]
                     [:link {:rel "shortcut icon" :href "/img/favicon.ico" :type "image/x-icon"}]

                     [:title (text :t.header/title)]
                     (include-css "/assets/bootstrap/css/bootstrap.min.css")
                     (include-css "/assets/font-awesome/css/all.css")
                     (include-css (cache-bust (css-filename context/*lang*)))
                     (for [extra-stylesheet (get-in env [:extra-stylesheets :files])]
                       (include-css (cache-bust extra-stylesheet)))]
                    (logo-preloads))
              [:body
               [:div#app app-content]
               (include-js "/assets/font-awesome/js/fontawesome.js")
               (include-js "/assets/better-dateinput-polyfill/dist/better-dateinput-polyfill.js")
               (include-js "/assets/jquery/jquery.min.js")
               (include-js "/assets/popper.js/dist/umd/popper.min.js")
               (include-js "/assets/tether/dist/js/tether.min.js")
               (include-js "/assets/bootstrap/js/bootstrap.min.js")
               (when (:accessibility-report env) (include-js "/assets/axe-core/axe.min.js"))
               (for [extra-script (get-in env [:extra-scripts :files])]
                 (include-js extra-script))
               content]]))))

(defn render
  "renders HTML generated by Hiccup

   params: :status -- status code to return, defaults to 200
           :headers -- map of headers to return, optional
           :content-type -- optional, defaults to \"text/html; charset=utf-8\""
  [content & [params]]
  (let [content-type (:content-type params "text/html; charset=utf-8")
        status (:status params 200)
        ;; we don't want to cache any HTML pages since they contain
        ;; references to cache-busted app.js and screen.css
        headers (merge {"Cache-Control" "no-store"}
                       (:headers params))]
    (response/content-type
     {:status status
      :headers headers
      :body (page-template content (:app-content params))}
     content-type)))

(defn home-page []
  (render
   (list
    [:script {:type "text/javascript"}
     (format "var csrfToken = '%s';"
             (when (bound? #'*anti-forgery-token*)
               *anti-forgery-token*))]
    (include-js (cache-bust "/js/app.js"))
    [:script {:type "text/javascript"} "rems.app.init();"]
    (inline-value "rems.app.setIdentity" {:user context/*user* :roles context/*roles*})
    (inline-value "rems.app.setConfig" (rems.service.public/get-config))
    (inline-value "rems.app.setTranslations" (rems.service.public/get-translations))
    (inline-value "rems.app.setTheme" (rems.service.public/get-theme))
    (when (contains? context/*roles* :handler)
      (inline-value "rems.app.setHandledOrganizations" (rems.service.organizations/get-handled-organizations (select-keys context/*user* [:userid]))))
    [:script {:type "text/javascript"} "rems.app.mount();"])))

(defn- error-content
  [error-details]
  [:div.container-fluid
   [:div.row-fluid
    [:div.col-lg-12
     [:div.centering.text-center
      [:div.text-center
       [:h1
        [:span.text-danger (str "Error: " (error-details :status))]
        [:hr]
        (when-let [title (error-details :title)]
          [:h2.without-margin title])
        (when-let [message (error-details :message)]
          [:h3.text-danger message])]]]]]])

(defn error-page
  "error-details should be a map containing the following keys:
   :status - error status
   :title - error title (optional)
   :message - detailed error message (optional)

   returns a response map with the error page as the body
   and the status specified by the status key"
  [error-details]
  (render nil (assoc error-details :app-content (error-content error-details))))
