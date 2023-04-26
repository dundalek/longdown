(ns longdown.cli
  (:require
   ["node:fs" :as fs]
   ["node:path" :as path]
   [clojure.string :as str]
   [clojure.tools.cli :as cli]
   [longdown.lib :as lib]))

(def cli-options
  [["-d" "--out-dir DIR" "Output directory"]
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

(defn convert-files [out-dir filenames]
  (doseq [in-path filenames]
    (let [out-path (path/join out-dir in-path)]
      (fs/mkdirSync (path/dirname out-path) #js {:recursive true})
      (->> (lib/slurp in-path)
           (lib/longform->outline)
           (lib/spit out-path)))))

(defn convert-from-stdin []
  (-> (lib/slurp (.-stdin.fd js/process))
      (lib/longform->outline)
      (str/trim-newline)
      (println)))

(defn -main [& args]
  (let [{:keys [options arguments summary]} (cli/parse-opts args cli-options)
        {:keys [out-dir]} options]
    (cond
      (or (:help options) (empty? arguments))
      (println (usage summary))

      (and (= (count arguments) 1) (= (first arguments) "-"))
      (convert-from-stdin)

      (nil? out-dir)
      (do
        (println "Output directory is required")
        (js/process.exit 1))

      :else (convert-files out-dir arguments))))
