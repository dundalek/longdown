(ns longdown.lib
  (:require
   ["mdast-util-to-markdown" :refer [defaultHandlers]]
   ["rehype-parse$default" :as rehype-parse]
   ["rehype-remark$default" :as rehype-remark]
   ["remark-parse$default" :as remark-parse]
   ["remark-stringify$default" :as remark-stringify]
   ["unified" :refer [unified]]
   [squint.string :as str]))

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
                                   {:type "list"
                                    :children children}]}]))))

    "list" [remaining
            {:type "listItem"
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
  (fn [tree _file cb]
    (let [next-tree (stratify tree)]
      (cb nil next-tree))))

(defn- onenterlineprefix [token]
  (this-as this
           (let [node (aget (.-stack this) (- (.. this -stack -length) 1))]
             (when (= (.-type node) "paragraph")
               (.call (.. this -config -enter -data) this token)))))

(defn- onexitlineprefix [token]
  (this-as this
           (let [node (aget (.-stack this) (- (.. this -stack -length) 1))]
             (when (= (.-type node) "text")
               (.call (.. this -config -exit -data) this token)))))

(def preserve-leading-whitespace-extension
  #js {:enter #js {:linePrefix onenterlineprefix}
       :exit #js {:linePrefix onexitlineprefix}})

(defn- filter-unsafe
  "Filter unsafe rules to prevent escaping of certain characters."
  [unsafe-arr]
  (.filter unsafe-arr
           (fn [x]
             (and (not (and (= (.-inConstruct x) "phrasing")
                            (or (= (.-character x) " ")
                                (= (.-character x) "\t"))))
                  (not= (.-character x) "_")
                  (not= (.-character x) "&")
                  (not= (.-character x) "[")
                  (not= (.-character x) "]")))))

(defn custom-paragraph [node _ state info]
  (let [orig-unsafe (.-unsafe state)
        _ (set! (.-unsafe state) (filter-unsafe orig-unsafe))
        value (.paragraph defaultHandlers node _ state info)
        _ (set! (.-unsafe state) orig-unsafe)]
    value))

(def stringify-options
  #js {:bullet "-"
       :listItemIndent "one"
       :fences true
       :join #js [(fn [_left _right _parent _state]
                    0)]
       :handlers #js {:paragraph custom-paragraph}})

(def ^:private markdown-parser
  (-> (unified)
      (.data "fromMarkdownExtensions" #js [preserve-leading-whitespace-extension])
      (.use remark-parse)
      (.use stratify-plugin)
      .freeze))

(def ^:private html-parser
  (-> (unified)
      (.use rehype-parse)
      (.use rehype-remark)
      (.use stratify-plugin)
      .freeze))

(def ^:private markdown-stringifier
  (-> (unified)
      (.use remark-stringify stringify-options)
      .freeze))

(defn parse-markdown
  "Parse markdown input to stratified AST."
  [input]
  (-> markdown-parser
      (.parse input)
      (->> (.runSync markdown-parser))))

(defn parse-html
  "Parse HTML input to stratified AST."
  [input]
  (-> html-parser
      (.parse input)
      (->> (.runSync html-parser))))

(defn stringify-markdown
  "Stringify AST to markdown string."
  [ast]
  (-> ast (->> (.stringify markdown-stringifier)) str))

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

(def longform->outline-stripped
  (make-converter {:strip-highlights? true}))

(def html->outline-stripped
  (make-converter {:html true :strip-highlights? true}))
