(ns ^:figwheel-always todo-frontend.core
    (:require-macros [cljs.core.async.macros :refer [go]])
    (:require [cljs.reader :as reader]
              [goog.events :as events]
              [om.core :as om :include-macros true]
              [om.dom :as dom :include-macros true]
              [cognitect.transit :as transit]
              [clojure.walk :as walk]
              [cljs.core.async :as async :refer [put! chan alts!]])
    (:import [goog.net XhrIo]
             [goog.net EventType]
             [goog.events EventType]))

(enable-console-print!)

(println "Edits to this text should show up in your developer console.")

;; define your app data so that it doesn't get over-written on reload

(def app-state (atom {:text "Hello world!"
                          :todos [{:todo/title "First post" :todo/order 1}
                                  {:todo/title "Get Dion a bigass monitor" :todo/order 2}
                                  {:todo/title "Eat a cookie" :todo/order 3}]}))

(def ^:private meths
  {:get "GET"
   :put "PUT"
   :post "POST"
   :delete "DELETE"})

(defn json-xhr [{:keys [method url data on-complete]}]
  (let [xhr (XhrIo.)]
    (events/listen
     xhr
     goog.net.EventType.COMPLETE
     (fn [e]
       (let [r (transit/reader :json)
             resp (walk/keywordize-keys (transit/read r (.getResponseText xhr)))]
         (on-complete (mapcat identity (get resp :response))))))
    (. xhr
      (send url (meths method) (when data (pr-str data))
            #js {"Content-Type" "application/json"}))))

(defn todos-view [data owner]
  (reify
    om/IRender
    (render [this]
      (dom/div nil
        (apply dom/ul nil
          (map (fn [todo] (dom/li nil (:todo/title todo))) (sort-by :todo/order (:todos data))))))))

(let [tx-chan (chan)
      tx-pub-chan (async/pub tx-chan (fn [_] :txs))]
  (json-xhr
   {:method :get
    :url "//localhost:8080/api/todo/v1/todo"
    :on-complete
    (fn [res]
      (swap! app-state assoc :todos res)
      (om/root todos-view app-state
               {:target (. js/document (getElementById "app"))
                :shared {:tx-chan tx-pub-chan}
                :tx-listen
                (fn [tx-data root-cursor]
                  (put! tx-chan [tx-chan root-cursor]))}))}))


(defn on-js-reload []
  ;; optionally touch your app-state to force rerendering depending on
  ;; your application
  (swap! app-state update-in [:__figwheel_counter] inc)
)
