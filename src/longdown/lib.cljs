(ns longdown.lib
  (:require
   ["node:fs" :as fs]
   ["mdast-util-from-markdown" :refer [fromMarkdown]]
   ["mdast-util-to-markdown" :refer [defaultHandlers]]
   ["rehype-parse" :as rehype-parse]
   ["rehype-remark" :as rehype-remark]
   ["remark-parse" :as remark-parse]
   ["remark-stringify" :as remark-stringify]
   ["unified" :refer [unified]]
   [clojure.string :as str]
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

(defn- strip-leading-number
  "Strip leading number pattern like '1. ' from text."
  [text]
  (str/replace text #"^\d+\.\s*" ""))

(defn- heading->paragraph
  "Convert a heading node to a paragraph, stripping leading numbers from text."
  [node]
  (-> node
      (assoc :type "paragraph")
      (dissoc :depth)
      (update :children
              (fn [children]
                (mapv (fn [child]
                        (if (= (:type child) "text")
                          (update child :value strip-leading-number)
                          child))
                      children)))))

(defn- empty-text-node? [node]
  (and (= (:type node) "text")
       (empty? (:value node))))

(defn- unwrap-leading-strong
  "If the first non-empty child is a strong node, unwrap its children to plain nodes."
  [children]
  (let [non-empty (drop-while empty-text-node? children)]
    (if-let [first-child (first non-empty)]
      (if (= (:type first-child) "strong")
        (concat (:children first-child) (rest non-empty))
        children)
      children)))

(defn- strip-leading-bold-in-node
  "Strip leading bold from paragraph or listItem children."
  [node]
  (if (contains? #{"paragraph" "listItem"} (:type node))
    (update node :children (comp vec unwrap-leading-strong))
    node))

(defn strip-highlights
  "Walk the AST and strip headers and leading bold formatting."
  [node]
  (let [transformed (cond
                      (= (:type node) "heading")
                      (-> node heading->paragraph strip-leading-bold-in-node)

                      (= (:type node) "paragraph")
                      (strip-leading-bold-in-node node)

                      :else node)]
    (if (:children transformed)
      (update transformed :children #(mapv strip-highlights %))
      transformed)))

(defn- stratify-plugin []
  (fn [tree file cb]
    (let [next-tree (-> tree
                        (js->clj :keywordize-keys true)
                        stratify
                        clj->js)]
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

;; To override: https://github.com/syntax-tree/mdast-util-to-markdown/blob/8ce8dbf681a29f0f33db91bcfffdabeb9345d609/lib/unsafe.js#L24
(defn- filter-unsafe
  "Filter unsafe rules to prevent escaping of certain characters."
  [unsafe-arr]
  (.filter unsafe-arr
           (fn [^js x]
             (and (not (and (= (.-inConstruct x) "phrasing")
                            (or (= (.-character x) " ")
                                (= (.-character x) "\t"))))
                  (not= (.-character x) "_")
                  (not= (.-character x) "&")
                  (not= (.-character x) "[")
                  (not= (.-character x) "]")))))

;; Override to prevent escaping spaces after newlines with `&#x20;`
(defn custom-paragraph [node _ ^js state info]
  (let [orig-unsafe (.-unsafe state)
        _ (set! (.-unsafe state) (filter-unsafe orig-unsafe))
        value (.paragraph defaultHandlers node _ state info)
        _ (set! (.-unsafe state) orig-unsafe)]
    value))

(def stringify-options
  #js {:bullet "-"
       :listItemIndent "one"
       ;; always use fenced code blocks
       :fences true
       ;; make output more compact with no extra newline between list items
       :join #js [(fn [_left _right _parent _state]
                    0)]
       :handlers #js {:paragraph custom-paragraph}})

(def ^:private markdown-parser
  (-> (unified)
      (.data "fromMarkdownExtensions" #js [preserve-leading-whitespace-extension])
      (.use remark-parse/default)
      (.use stratify-plugin)
      .freeze))

(def ^:private html-parser
  (-> (unified)
      (.use rehype-parse/default)
      (.use rehype-remark/default)
      (.use stratify-plugin)
      .freeze))

(def ^:private markdown-stringifier
  (-> (unified)
      (.use remark-stringify/default stringify-options)
      .freeze))

(defn parse-markdown
  "Parse markdown input to stratified AST (Clojure data)."
  [input]
  (-> markdown-parser
      (.parse input)
      (->> (.runSync markdown-parser))
      (js->clj :keywordize-keys true)))

(defn parse-html
  "Parse HTML input to stratified AST (Clojure data)."
  [input]
  (-> html-parser
      (.parse input)
      (->> (.runSync html-parser))
      (js->clj :keywordize-keys true)))

(defn stringify-markdown
  "Stringify AST (Clojure data) to markdown string."
  [ast]
  (-> ast clj->js (->> (.stringify markdown-stringifier)) str))

(defn make-converter
  "Create a converter function from options.
   - :html - parse as HTML instead of markdown
   - :strip-highlights - strip headers and leading bold formatting"
  [{:keys [html strip-highlights?]}]
  (let [parse-fn (if html parse-html parse-markdown)]
    (if strip-highlights?
      (comp stringify-markdown strip-highlights parse-fn)
      (comp stringify-markdown parse-fn))))

(def longform->outline
  (make-converter {}))

(def html->outline
  (make-converter {:html true}))

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
