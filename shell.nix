with import <nixpkgs> { };
stdenv.mkDerivation {
  name = "dev-env";
  buildInputs = [
    babashka
    clojure
    watchexec
  ];
}
