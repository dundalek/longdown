(ns longdown.cli
  (:require
   ["node:fs" :as fs]
   ["node:path" :as path]
   ["node:util" :refer [parseArgs]]
   [squint.string :as str]
   [longdown.lib :as lib]))

(def cli-options
  #js {:help          #js {:type "boolean" :short "h"}
       :out-dir       #js {:type "string"  :short "d"}
       :html          #js {:type "boolean"}
       :strip-highlights #js {:type "boolean"}})

(def usage-text
  (str/join \newline
    ["Convert longform markdown files to outline format used by Logseq"
     ""
     "Usage: longdown [options] file.md..."
     "       longdown -"
     ""
     "When file is -, reads standard input and prints to standard output."
     ""
     "Options:"
     "  -d, --out-dir DIR       Output directory"
     "      --html              Read HTML input instead of markdown"
     "      --strip-highlights  Strip headers and leading bold formatting"
     "  -h, --help              Show this help"]))

(defn convert-files [out-dir filenames convert-fn]
  (doseq [in-path filenames]
    (let [out-path (path/join out-dir in-path)]
      (fs/mkdirSync (path/dirname out-path) #js {:recursive true})
      (->> (lib/slurp in-path)
           convert-fn
           (lib/spit out-path)))))

(defn convert-from-stdin [convert-fn]
  (-> (lib/slurp (.-stdin.fd js/process))
      convert-fn
      (.replace #"\n+$" "")
      (println)))

(defn ^:export -main [& _args]
  (let [parsed (parseArgs #js {:options cli-options
                                :allowPositionals true
                                :args (.slice (.-argv js/process) 2)})
        opts (.-values parsed)
        arguments (.-positionals parsed)
        html (.-html opts)
        strip-highlights (.-strip-highlights opts)
        out-dir (aget opts "out-dir")
        convert-fn (lib/make-converter {:html html
                                        :strip-highlights? strip-highlights})]
    (cond
      (or (.-help opts) (empty? arguments))
      (println usage-text)

      (and (= (count arguments) 1) (= (first arguments) "-"))
      (convert-from-stdin convert-fn)

      (nil? out-dir)
      (do
        (println "Output directory is required")
        (js/process.exit 1))

      :else (convert-files out-dir arguments convert-fn))))
