- Top-level paragraph
  another line
-
  - top
    - level
    - list
  - here
- another paragraph
- # with nested headings
  - nested paragraph
  - ## h2
    - another nesting
    -
      - a
      - b
        - c
        - d
          ddd
- # headings with skipped levels
  - ### h3
    - para 3
  - ## h2
    - para 2
- # numbered lists
  - numbered list ends up as a blog of text under a single list item which is the same behavior when creating ordered lists in logseq with auto-numbering
  -
    1. first
    2. second
    3. third
  - numbered lists are normalized to dot notation
  -
    1. first
    2. second
    3. third
- # lists next to each other
  - may be nice to treat them as separate lists, but markdown seems to join the into a single list
  -
    - a
    - b
    - c
    - d
    - e
    - f
- # different list bullet types are normalized to dashes
  -
    - a
      - b
        - c
- # whitespace nesting
  - when lazy using lists to indent, does not work at the moment, will be rolled into a single paragraph
  - some
      nested
        anotherlevel of nesting
        another line
      abc
        xyz
          zzz
  - another nested example
  - with newlines
        some other note
        xyz
  - between
        items
- # code block
  - ```
    $ echo abc
    ```
- # indented code blocks
  - ```
    let a = 1
    let b = 2
    ```
  - some text
        this is not a code block even though 4 spaces indented
- # top level block quote
  - > a
    > b
- # mixed paragraphs and lists
  -
    - block 1
      - child of block 1
        block 2
        still block 2
        still block 2
    - block 3
  - block 1
  -
    - block 2
      block 3
      still block 3
      still block 3
    - block 4
