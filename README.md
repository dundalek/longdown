# Longdown

Convert longform markdown files to outline format used by Logseq.

## Motivation

I've been using text editor to write my notes as markdown files and rely on the headings structure to organize long notes.

However, when I tried to move my markdown files to Logseq, I faced a problem: the structure wasn't preserved which resulted in a loss of meaning.

This tool helps with transition by converting existing longform markdown files to outline format that preserves the structure in a way that fits well for Logseq.

## Example

For example:
```
# h1

paragraph

## h2

next paragraph

another paragraph
```

Gets converted into:
```
- # h1
  - paragraph
  - ## h2
    - next paragraph
    - another paragraph
```


## Usage

Install with:

```
npm install -g longdown
```

#### Converting files

It is a good idea to make a backup of the input files first before running the script!

Specify output directory with `-d` flag and pass input files:

```
cd my-notes
longdown -d ../out *.md
```

Double-check the output, for example compare using the [Meld](https://meldmerge.org/) tool:
```
cd ..
meld my-notes out
```

#### Converting text snippets

Use `longdown -` to read from standard input and print result to standard output.

It can be useful to converting text snippets from clipboard and pasting them directly to Logseq without needing to create intermediate files.

For example on Linux:
```
xclip -selection clipboard -out | longdown - | xclip -selection clipboard -in
```

On macOS:
```
pbpaste | longdown - | pbcopy
```

## License

0BSD

## Development

Install [Babashka](https://babashka.org/) to run development tasks.

List available tasks:
```
bb tasks
```

Run the CLI locally:
```
bin/cli.js
```
