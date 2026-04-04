(ns longdown.lib-test
  (:require ["node:test" :refer [describe it]]
            ["node:assert/strict" :as assert]
            ["node:fs" :as fs]
            [longdown.lib :as lib]))

(describe "longform->outline"
  (fn []
    (it "converts paragraphs to list items"
      (fn []
        (assert/equal (lib/longform->outline "a\n\nb")
                      "- a\n- b\n")))

    (it "converts headings with content"
      (fn []
        (assert/equal (lib/longform->outline "# a\npara a\n# b\npara b")
                      "- # a\n  - para a\n- # b\n  - para b\n")))

    (it "handles nested headings"
      (fn []
        (assert/equal (lib/longform->outline "# h1\n## h2\npara")
                      "- # h1\n  - ## h2\n    - para\n")))

    (it "preserves nested text indentation"
      (fn []
        (assert/equal (lib/longform->outline "some\n  nested\n    text\n")
                      "- some\n    nested\n      text\n")))

    (it "lists end up nested so they are distinguished from surrounding paragraphs"
      (fn []
        (assert/equal (lib/longform->outline "paragraph\n\n- item 1\n- item 2\n\nother paragraph")
                      "- paragraph\n-\n  - item 1\n  - item 2\n- other paragraph\n")))

    (it "horizontal separators are normalized using asterisks"
      (fn []
        (assert/equal (lib/longform->outline "abc\n\n----\n\nxyz")
                      "- abc\n- ***\n- xyz\n")))

    (it "does not escape leading whitespace in paragraphs"
      (fn []
        (assert/equal (lib/longform->outline "text\n  indented")
                      "- text\n    indented\n")
        (assert/equal (lib/longform->outline "text\n\tindented")
                      "- text\n  \tindented\n")))

    (it "does not escape underscores inside a link"
      (fn []
        (assert/equal (lib/longform->outline "https://example.com/path_with_underscores\n")
                      "- https://example.com/path_with_underscores\n")))

    (it "does not escape ampersands inside a link"
      (fn []
        (assert/equal (lib/longform->outline "https://example.com?a=1&b=2\n")
                      "- https://example.com?a=1&b=2\n")))

    (it "does not escape brackets inside list"
      (fn []
        (assert/equal (lib/longform->outline "- [ ] foo\n- [x] bar\n")
                      "-\n  - [ ] foo\n  - [x] bar\n")))))

(describe "html->outline"
  (fn []
    (it "converts paragraphs"
      (fn []
        (assert/equal (lib/html->outline "<p>a</p><p>b</p>")
                      "- a\n- b\n")))

    (it "converts headings with content"
      (fn []
        (assert/equal (lib/html->outline "<h1>a</h1><p>para a</p><h1>b</h1><p>para b</p>")
                      "- # a\n  - para a\n- # b\n  - para b\n")))

    (it "handles nested headings"
      (fn []
        (assert/equal (lib/html->outline "<h1>h1</h1><h2>h2</h2><p>para</p>")
                      "- # h1\n  - ## h2\n    - para\n")))

    (it "inline formatting"
      (fn []
        (assert/equal (lib/html->outline "<p>text with <strong>bold</strong> and <em>italic</em></p>")
                      "- text with **bold** and *italic*\n")))

    (it "lists"
      (fn []
        (assert/equal (lib/html->outline "<p>paragraph</p><ul><li>item 1</li><li>item 2</li></ul>")
                      "- paragraph\n-\n  - item 1\n  - item 2\n")))

    (it "code blocks"
      (fn []
        (assert/equal (lib/html->outline "<pre><code>code</code></pre>")
                      "- ```\n  code\n  ```\n")))

    (it "links"
      (fn []
        (assert/equal (lib/html->outline "<p><a href=\"https://example.com\">link text</a></p>")
                      "- [link text](https://example.com)\n")))))

(describe "strip-highlights"
  (fn []
    (it "strips header markers and numbers"
      (fn []
        (assert/equal (lib/longform->outline-stripped "# Title") "- Title\n")
        (assert/equal (lib/longform->outline-stripped "## Title") "- Title\n")
        (assert/equal (lib/longform->outline-stripped "# 1. Title") "- Title\n")
        (assert/equal (lib/longform->outline-stripped "## 2. Title") "- Title\n")))

    (it "strips leading bold"
      (fn []
        (assert/equal (lib/longform->outline-stripped "**Text**: rest") "- Text: rest\n")
        (assert/equal (lib/longform->outline-stripped "**Text** rest") "- Text rest\n")))

    (it "strips bold spanning entire heading"
      (fn []
        (assert/equal (lib/longform->outline-stripped "### 2. **Popup Menu System**") "- Popup Menu System\n")))

    (it "strips bold spanning entire list item"
      (fn []
        (assert/equal (lib/longform->outline-stripped "- **Bold Item**") "-\n  - Bold Item\n")))

    (it "preserves bold inside text"
      (fn []
        (assert/equal (lib/longform->outline-stripped "some **word** foo") "- some **word** foo\n")))

    (it "preserves nesting"
      (fn []
        (assert/equal (lib/longform->outline-stripped "# Title\nContent") "- Title\n  - Content\n")))

    (it "full example from requirements"
      (fn []
        (assert/equal (lib/longform->outline-stripped "## 1. Introduction\n\n**Key point**: This is important.\n\n- **First item**: description here\n- Regular item with **emphasis** in the middle")
                      "- Introduction\n  - Key point: This is important.\n  -\n    - First item: description here\n    - Regular item with **emphasis** in the middle\n")))

    (it "strips headers and leading bold from HTML"
      (fn []
        (assert/equal (lib/html->outline-stripped "<h2>1. Introduction</h2><p><strong>Key point</strong>: This is important.</p>")
                      "- Introduction\n  - Key point: This is important.\n")))

    (it "strips leading bold in HTML list items"
      (fn []
        (assert/equal (lib/html->outline-stripped "<ul><li><strong>First item</strong>: description</li><li>normal item</li></ul>")
                      "-\n  - First item: description\n  - normal item\n")))))

(describe "snapshot"
  (fn []
    (it "matches approved output"
      (fn []
        (let [actual (lib/longform->outline (fs/readFileSync "test/resources/sample.md" "utf-8"))
              expected (fs/readFileSync "test/resources/sample.approved.md" "utf-8")]
          (assert/equal actual expected))))))
