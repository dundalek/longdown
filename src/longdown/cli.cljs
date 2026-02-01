(ns longdown.cli
  (:require
   ["node:fs" :as fs]
   ["node:path" :as path]
   [clojure.string :as str]
   [clojure.tools.cli :as cli]
   [longdown.lib :as lib]))

(def cli-options
  [["-d" "--out-dir DIR" "Output directory"]
   [nil "--html" "Read HTML input instead of markdown"]
   ["-h" "--help"]])

(defn usage [options-summary]
  (->> ["Convert longform markdown files to outline format used by Logseq"
        ""
        "Usage: longdown [options] file.md..."
        "       longdown -"
        ""
        "When file is -, reads standard input and prints to standard output."
        ""
        "Options:"
        options-summary]
       (str/join \newline)))

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
      (str/trim-newline)
      (println)))

(defn -main [& args]
  (let [{:keys [options arguments summary]} (cli/parse-opts args cli-options)
        {:keys [out-dir html]} options
        convert-fn (if html lib/html->outline lib/longform->outline)]
    (cond
      (or (:help options) (empty? arguments))
      (println (usage summary))

      (and (= (count arguments) 1) (= (first arguments) "-"))
      (convert-from-stdin convert-fn)

      (nil? out-dir)
      (do
        (println "Output directory is required")
        (js/process.exit 1))

      :else (convert-files out-dir arguments convert-fn))))
