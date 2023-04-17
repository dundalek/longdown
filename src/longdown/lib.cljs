(ns longdown.lib
  (:require
   ["node:fs" :as fs]
   ["mdast-util-from-markdown" :refer [fromMarkdown]]
   ["mdast-util-to-markdown" :refer [defaultHandlers]]
   ["mdast-util-to-markdown/lib/unsafe.js" :refer [unsafe]]
   ["remark-parse" :as remark-parse]
   ["remark-stringify" :as remark-stringify]
   ["unified" :refer [unified]]
   [clojure.walk :as walk]))

(defn slurp [path]
  (fs/readFileSync path "utf-8"))

(defn spit [path output]
  (fs/writeFileSync path output))

(defn- stratify-nodes [[node & remaining]]
  (case (:type node)
    "heading" (let [{:keys [depth]} node]
                (loop [nodes remaining
                       children []]
                  (let [child (first nodes)]
                    (if (and child
                             (or (not= (:type child) "heading")
                                 (< depth (:depth child))))
                      (let [[nodes child] (stratify-nodes nodes)]
                        (recur nodes (conj children child)))
                      [nodes
                       {:type "listItem"
                        :children [node
                                     ; Strip headings, maybe make it an option
                                     ; (assoc node :type "paragraph")
                                   {:type "list"
                                    :children children}]}]))))

      ;; Add some special handling for :ordered list?
    "list" [remaining
            {:type "listItem"
                 ;; Pad the list with empty paragraph, will end up
                 ;; as a list under a blank node in Logseq
             :children [{:type "paragraph"
                         :children [{:type "text"
                                     :text ""}]}
                        node]}]

    [remaining
     {:type "listItem"
      :children [node]}]))

(defn stratify [node]
  (assert (= (:type node) "root"))
  (loop [nodes (:children node)
         children []]
    (if (seq nodes)
      (let [[nodes child] (stratify-nodes nodes)]
        (recur nodes (conj children child)))
      (assoc node
             :children [{:type "list"
                         :children children}]))))

(defn process [_options]
  (fn [tree file cb]
    (let [next-tree (-> tree
                        (js->clj :keywordize-keys true)
                        stratify
                        (clj->js))]
      (cb nil next-tree file))))

(defn- onenterlineprefix [token]
  (this-as ^js this
           (let [node (aget (.-stack this) (- (.. this -stack -length) 1))]
             (when (= (.-type node) "paragraph")
               (.call (.. this -config -enter -data) this token)))))

(defn- onexitlineprefix [token]
  (this-as ^js this
           (let [node (aget (.-stack this) (- (.. this -stack -length) 1))]
             (when (= (.-type node) "text")
               (.call (.. this -config -exit -data) this token)))))

(def preserve-leading-whitespace-extension
  #js {:enter #js {:linePrefix onenterlineprefix}
       :exit #js {:linePrefix onexitlineprefix}})

(def custom-unsafe
  (.filter unsafe
           (fn [^js x]
             (not
              (and (= (.-before x) "[\\r\\n]")
                   (= (.-character x) " ")
                   (= (.-inConstruct x) "phrasing"))))))

;; Override to prevent escaping spaces after newlines with `&#x20;`
(defn custom-paragraph [node _ ^js state info]
  (let [orig-unsafe (.-unsafe state)
        _ (set! (.-unsafe state) custom-unsafe)
        value (.paragraph defaultHandlers node _ state info)
        _ (set! (.-unsafe state) orig-unsafe)]
    value))

(defn longform->outline [input]
  (-> (unified)
      (.data "fromMarkdownExtensions" #js [preserve-leading-whitespace-extension])
      (.use remark-parse/default)
      (.use process)
      (.use remark-stringify/default
            #js {:bullet "-"
                 :listItemIndent "one"
                 ;; always use fenced code blocks
                 :fences true
                 ;; make output more compact with no extra newline between list items
                 :join #js [(fn [_left _right _parent _state]
                              0)]
                 :handlers #js {:paragraph custom-paragraph}})
      (.processSync input)
      str))

(comment
  (println (longform->outline (slurp "tmp.md")))

  (defn- strip-position [node]
    (walk/prewalk
     (fn [x]
       (if (map? x)
         (dissoc x :position)
         x))
     node))

  (-> (fromMarkdown "a\n  b\n  c")
      (js->clj :keywordize-keys true)
      strip-position))
