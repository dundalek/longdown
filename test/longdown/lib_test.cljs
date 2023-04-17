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
           (lib/longform->outline "abc\n\n----\n\nxyz")))))

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
