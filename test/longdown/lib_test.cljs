(ns longdown.lib-test
  (:require
   [clojure.test :as t :refer [deftest is testing]]
   [longdown.lib :as lib]))

(deftest stratify-test
  (is (= {:type "root"
          :children [{:type "list"
                      :children []}]}
         (lib/stratify {:type "root"
                        :children []})))

  (is (= {:type "root"
          :children [{:type "list"
                      :children [{:type "listItem"
                                  :children [{:type "paragraph" :key :p-a}]}]}]}
         (lib/stratify {:type "root"
                        :children [{:type "paragraph" :key :p-a}]})))

  (is (= {:type "root"
          :children [{:type "list"
                      :children [{:type "listItem"
                                  :children [{:type "paragraph" :key :p-a}]}
                                 {:type "listItem"
                                  :children [{:type "heading" :depth 1 :key :h-a}
                                             {:type "list"
                                              :children [{:type "listItem"
                                                          :children [{:type "paragraph" :key :p-b}]}]}]}]}]}
         (lib/stratify {:type "root"
                        :children [{:type "paragraph" :key :p-a}
                                   {:type "heading" :depth 1 :key :h-a}
                                   {:type "paragraph" :key :p-b}]})))

  (is (= {:type "root"
          :children [{:type "list"
                      :children [{:type "listItem"
                                  :children [{:type "heading" :depth 1 :key :h-a}
                                             {:type "list"
                                              :children [{:type "listItem"
                                                          :children [{:type "paragraph" :key :p-a}]}]}]}
                                 {:type "listItem"
                                  :children [{:type "heading" :depth 1 :key :h-b}
                                             {:type "list"
                                              :children [{:type "listItem"
                                                          :children [{:type "paragraph" :key :p-b}]}]}]}]}]}
         (lib/stratify {:type "root"
                        :children [{:type "heading" :depth 1 :key :h-a}
                                   {:type "paragraph" :key :p-a}
                                   {:type "heading" :depth 1 :key :h-b}
                                   {:type "paragraph" :key :p-b}]})))

  (is (= {:type "root"
          :children [{:type "list"
                      :children [{:type "listItem"
                                  :children [{:type "heading" :depth 1 :key :h-a}
                                             {:type "list"
                                              :children [{:type "listItem"
                                                          :children [{:type "paragraph" :key :p-a}]}
                                                         {:type "listItem"
                                                          :children [{:type "heading" :depth 2 :key :h-b}
                                                                     {:type "list"
                                                                      :children [{:type "listItem"
                                                                                  :children [{:type "paragraph" :key :p-b}]}]}]}]}]}]}]}
         (lib/stratify {:type "root"
                        :children [{:type "heading" :depth 1 :key :h-a}
                                   {:type "paragraph" :key :p-a}
                                   {:type "heading" :depth 2 :key :h-b}
                                   {:type "paragraph" :key :p-b}]}))))

(deftest longform->outline-test
  (is (= "- a\n- b\n"
         (lib/longform->outline "a\n\nb")))

  (is (= "- # a\n  - para a\n- # b\n  - para b\n"
         (lib/longform->outline "# a\npara a\n# b\npara b")))

  (is (= "- # h1\n  - ## h2\n    - para\n"
         (lib/longform->outline "# h1\n## h2\npara")))

  (is (= "- some\n    nested\n      text\n"
         (lib/longform->outline "some\n  nested\n    text\n")))

  (testing "lists end up nested so they are distinguished from surrounding paragraphs"
    (is (= "- paragraph\n-\n  - item 1\n  - item 2\n- other paragraph\n"
           (lib/longform->outline "paragraph\n\n- item 1\n- item 2\n\nother paragraph"))))

  (testing "horizonal separators are normalized using asterisks so they do not interfere with bullet dashes"
    (is (= "- abc\n- ***\n- xyz\n"
           (lib/longform->outline "abc\n\n----\n\nxyz"))))

  (testing "does not escape leading whitescape in paragraphs"
    (is (= "- text\n    indented\n" (lib/longform->outline "text\n  indented")))
    (is (= "- text\n  \tindented\n" (lib/longform->outline "text\n\tindented"))))

  (testing "does not escape underscores inside a link"
    (is (= "- https://example.com/path_with_underscores\n"
           (lib/longform->outline "https://example.com/path_with_underscores\n"))))

  (testing "does not escape ampersands inside a link"
    (is (= "- https://example.com?a=1&b=2\n"
           (lib/longform->outline "https://example.com?a=1&b=2\n"))))

  (testing "does not escape brackets inside list"
    (is (= "-\n  - [ ] foo\n  - [x] bar\n"
           (lib/longform->outline "- [ ] foo\n- [x] bar\n")))))

(deftest strip-highlights-test
  (let [convert (lib/make-converter {:strip-highlights? true})]
    (testing "strips header markers and numbers"
      (is (= "- Title\n" (convert "# Title")))
      (is (= "- Title\n" (convert "## Title")))
      (is (= "- Title\n" (convert "# 1. Title")))
      (is (= "- Title\n" (convert "## 2. Title"))))

    (testing "strips leading bold"
      (is (= "- Text: rest\n" (convert "**Text**: rest")))
      (is (= "- Text rest\n" (convert "**Text** rest"))))

    (testing "strips bold spanning entire heading"
      (is (= "- Popup Menu System\n" (convert "### 2. **Popup Menu System**"))))

    (testing "strips bold spanning entire list item"
      (is (= "-\n  - Bold Item\n" (convert "- **Bold Item**"))))

    (testing "preserves bold inside text"
      (is (= "- some **word** foo\n" (convert "some **word** foo"))))

    (testing "preserves nesting"
      (is (= "- Title\n  - Content\n" (convert "# Title\nContent"))))

    (testing "full example from requirements"
      (is (= "- Introduction\n  - Key point: This is important.\n  -\n    - First item: description here\n    - Regular item with **emphasis** in the middle\n"
             (convert "## 1. Introduction\n\n**Key point**: This is important.\n\n- **First item**: description here\n- Regular item with **emphasis** in the middle")))))

  (let [convert-html (lib/make-converter {:html true :strip-highlights? true})]
    (testing "strips headers and leading bold from HTML"
      (is (= "- Introduction\n  - Key point: This is important.\n"
             (convert-html "<h2>1. Introduction</h2><p><strong>Key point</strong>: This is important.</p>"))))

    (testing "strips leading bold in HTML list items"
      (is (= "-\n  - First item: description\n  - normal item\n"
             (convert-html "<ul><li><strong>First item</strong>: description</li><li>normal item</li></ul>"))))))

(deftest html->outline-test
  (is (= "- a\n- b\n"
         (lib/html->outline "<p>a</p><p>b</p>")))

  (is (= "- # a\n  - para a\n- # b\n  - para b\n"
         (lib/html->outline "<h1>a</h1><p>para a</p><h1>b</h1><p>para b</p>")))

  (is (= "- # h1\n  - ## h2\n    - para\n"
         (lib/html->outline "<h1>h1</h1><h2>h2</h2><p>para</p>")))

  (testing "inline formatting"
    (is (= "- text with **bold** and *italic*\n"
           (lib/html->outline "<p>text with <strong>bold</strong> and <em>italic</em></p>"))))

  (testing "lists"
    (is (= "- paragraph\n-\n  - item 1\n  - item 2\n"
           (lib/html->outline "<p>paragraph</p><ul><li>item 1</li><li>item 2</li></ul>"))))

  (testing "code blocks"
    (is (= "- ```\n  code\n  ```\n"
           (lib/html->outline "<pre><code>code</code></pre>"))))

  (testing "links"
    (is (= "- [link text](https://example.com)\n"
           (lib/html->outline "<p><a href=\"https://example.com\">link text</a></p>")))))

;; Approval Tests technique
;; ---
;; To approve changes to make tests pass run:
;; mv test/resources/sample.received.md test/resources/sample.approved.md
(deftest snapshot-test
  (let [actual (lib/longform->outline (lib/slurp "test/resources/sample.md"))]
    (lib/spit "test/resources/sample.received.md" actual)

    (is (= (lib/slurp "test/resources/sample.approved.md")
           actual))))

(defn run-tests []
  (t/run-tests 'longdown.lib-test))

(comment
  ;; print name of each test
  (defmethod t/report [:cljs.test/default :begin-test-var] [m]
    (println "===" (-> m :var meta :name))
    (println))

  (run-tests))
