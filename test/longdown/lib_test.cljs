(ns longdown.lib-test
  (:require ["node:test" :refer [describe it]]
            ["node:assert/strict" :as assert]
            ["node:fs" :as fs]
            ["../../src/index.mjs" :as api]))

(describe "longform->outline"
  (fn []
    (it "converts paragraphs to list items"
      (fn []
        (assert/equal (api/longformToOutline "a\n\nb")
                      "- a\n- b\n")))

    (it "converts headings with content"
      (fn []
        (assert/equal (api/longformToOutline "# a\npara a\n# b\npara b")
                      "- # a\n  - para a\n- # b\n  - para b\n")))

    (it "handles nested headings"
      (fn []
        (assert/equal (api/longformToOutline "# h1\n## h2\npara")
                      "- # h1\n  - ## h2\n    - para\n")))

    (it "preserves nested text indentation"
      (fn []
        (assert/equal (api/longformToOutline "some\n  nested\n    text\n")
                      "- some\n    nested\n      text\n")))

    (it "lists end up nested so they are distinguished from surrounding paragraphs"
      (fn []
        (assert/equal (api/longformToOutline "paragraph\n\n- item 1\n- item 2\n\nother paragraph")
                      "- paragraph\n-\n  - item 1\n  - item 2\n- other paragraph\n")))

    (it "horizontal separators are normalized using asterisks"
      (fn []
        (assert/equal (api/longformToOutline "abc\n\n----\n\nxyz")
                      "- abc\n- ***\n- xyz\n")))

    (it "does not escape leading whitespace in paragraphs"
      (fn []
        (assert/equal (api/longformToOutline "text\n  indented")
                      "- text\n    indented\n")
        (assert/equal (api/longformToOutline "text\n\tindented")
                      "- text\n  \tindented\n")))

    (it "does not escape underscores inside a link"
      (fn []
        (assert/equal (api/longformToOutline "https://example.com/path_with_underscores\n")
                      "- https://example.com/path_with_underscores\n")))

    (it "does not escape ampersands inside a link"
      (fn []
        (assert/equal (api/longformToOutline "https://example.com?a=1&b=2\n")
                      "- https://example.com?a=1&b=2\n")))

    (it "does not escape brackets inside list"
      (fn []
        (assert/equal (api/longformToOutline "- [ ] foo\n- [x] bar\n")
                      "-\n  - [ ] foo\n  - [x] bar\n")))))

(describe "html->outline"
  (fn []
    (it "converts paragraphs"
      (fn []
        (assert/equal (api/htmlToOutline "<p>a</p><p>b</p>")
                      "- a\n- b\n")))

    (it "converts headings with content"
      (fn []
        (assert/equal (api/htmlToOutline "<h1>a</h1><p>para a</p><h1>b</h1><p>para b</p>")
                      "- # a\n  - para a\n- # b\n  - para b\n")))

    (it "handles nested headings"
      (fn []
        (assert/equal (api/htmlToOutline "<h1>h1</h1><h2>h2</h2><p>para</p>")
                      "- # h1\n  - ## h2\n    - para\n")))

    (it "inline formatting"
      (fn []
        (assert/equal (api/htmlToOutline "<p>text with <strong>bold</strong> and <em>italic</em></p>")
                      "- text with **bold** and *italic*\n")))

    (it "lists"
      (fn []
        (assert/equal (api/htmlToOutline "<p>paragraph</p><ul><li>item 1</li><li>item 2</li></ul>")
                      "- paragraph\n-\n  - item 1\n  - item 2\n")))

    (it "code blocks"
      (fn []
        (assert/equal (api/htmlToOutline "<pre><code>code</code></pre>")
                      "- ```\n  code\n  ```\n")))

    (it "links"
      (fn []
        (assert/equal (api/htmlToOutline "<p><a href=\"https://example.com\">link text</a></p>")
                      "- [link text](https://example.com)\n")))))

(def ^:private strip-opts #js {:stripHighlights true})

(describe "strip-highlights"
  (fn []
    (it "strips header markers and numbers"
      (fn []
        (assert/equal (api/longformToOutline "# Title" strip-opts) "- Title\n")
        (assert/equal (api/longformToOutline "## Title" strip-opts) "- Title\n")
        (assert/equal (api/longformToOutline "# 1. Title" strip-opts) "- Title\n")
        (assert/equal (api/longformToOutline "## 2. Title" strip-opts) "- Title\n")))

    (it "strips leading bold"
      (fn []
        (assert/equal (api/longformToOutline "**Text**: rest" strip-opts) "- Text: rest\n")
        (assert/equal (api/longformToOutline "**Text** rest" strip-opts) "- Text rest\n")))

    (it "strips bold spanning entire heading"
      (fn []
        (assert/equal (api/longformToOutline "### 2. **Popup Menu System**" strip-opts) "- Popup Menu System\n")))

    (it "strips bold spanning entire list item"
      (fn []
        (assert/equal (api/longformToOutline "- **Bold Item**" strip-opts) "-\n  - Bold Item\n")))

    (it "preserves bold inside text"
      (fn []
        (assert/equal (api/longformToOutline "some **word** foo" strip-opts) "- some **word** foo\n")))

    (it "preserves nesting"
      (fn []
        (assert/equal (api/longformToOutline "# Title\nContent" strip-opts) "- Title\n  - Content\n")))

    (it "full example from requirements"
      (fn []
        (assert/equal (api/longformToOutline "## 1. Introduction\n\n**Key point**: This is important.\n\n- **First item**: description here\n- Regular item with **emphasis** in the middle" strip-opts)
                      "- Introduction\n  - Key point: This is important.\n  -\n    - First item: description here\n    - Regular item with **emphasis** in the middle\n")))

    (it "strips headers and leading bold from HTML"
      (fn []
        (assert/equal (api/htmlToOutline "<h2>1. Introduction</h2><p><strong>Key point</strong>: This is important.</p>" strip-opts)
                      "- Introduction\n  - Key point: This is important.\n")))

    (it "strips leading bold in HTML list items"
      (fn []
        (assert/equal (api/htmlToOutline "<ul><li><strong>First item</strong>: description</li><li>normal item</li></ul>" strip-opts)
                      "-\n  - First item: description\n  - normal item\n")))))

(describe "snapshot"
  (fn []
    (it "matches approved output"
      (fn []
        (let [actual (api/longformToOutline (fs/readFileSync "test/resources/sample.md" "utf-8"))
              expected (fs/readFileSync "test/resources/sample.approved.md" "utf-8")]
          (assert/equal actual expected))))))
