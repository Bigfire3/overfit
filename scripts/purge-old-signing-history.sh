#!/usr/bin/env bash
# Entfernt den alten (seit Umstellung auf GitHub-Secrets ersetzten) Keystore und dessen Passwörter
# aus der gesamten Git-Historie und überschreibt danach alle Branches und Tags auf GitHub.
#
# Einmalig ausführen, bevor das Repo öffentlich wird, und zwar:
#   - NACHDEM der PR mit dem neuen Signatur-Setup in main gemergt ist,
#   - wenn keine anderen offenen PRs/Branches mehr gebraucht werden (deren Historie passt danach nicht mehr),
#   - mit vorübergehend erlaubtem Force-Push auf main (Branch-Schutz), falls aktiv.
#
# Voraussetzung: git, git-filter-repo (pip install git-filter-repo), Schreibrechte auf das Repo.
# Aufruf: scripts/purge-old-signing-history.sh [repo-url]
set -euo pipefail

REPO_URL="${1:-https://github.com/Bigfire3/overfit.git}"
command -v git-filter-repo >/dev/null || { echo "git-filter-repo fehlt: pip install git-filter-repo" >&2; exit 1; }

WORK="$(mktemp -d)"
echo "Arbeitsverzeichnis: $WORK"
git clone --no-local --bare "$REPO_URL" "$WORK/overfit.git"
cd "$WORK/overfit.git"
git fetch origin '+refs/heads/*:refs/heads/*' '+refs/tags/*:refs/tags/*'

# Passwort-Zeilen der alten Signing-Config neutralisieren, ohne das alte Passwort hier zu nennen.
cat > "$WORK/replacements.txt" <<'RULES'
regex:(store|key)Password = "(?!ENTFERNT)[^"$]+"==>\1Password = "ENTFERNT"
RULES
git filter-repo --force --invert-paths --path signing/ --path-glob '*.jks' --replace-text "$WORK/replacements.txt"

# Prüfen: keine Keystores und keine fest eingetragenen Passwörter mehr.
if git log --all --name-only --format= | grep -E '(^signing/|\.jks$)' >/dev/null; then
  echo "Fehler: Keystore noch in der Historie" >&2; exit 1
fi
if git log --all -p | grep -E '^\+.*(store|key)Password = "[^"]' | grep -v 'ENTFERNT' >/dev/null; then
  echo "Fehler: Passwort noch in der Historie" >&2; exit 1
fi
echo "Historie bereinigt. Branches: $(git branch | wc -l), Tags: $(git tag | wc -l)"

read -r -p "Jetzt alle Branches und Tags auf $REPO_URL überschreiben? (ja/nein) " answer
[ "$answer" = "ja" ] || { echo "Abgebrochen, nichts gepusht. Ergebnis liegt in $WORK/overfit.git"; exit 0; }
git remote add origin "$REPO_URL"
git push --force origin 'refs/heads/*:refs/heads/*' 'refs/tags/*:refs/tags/*'
echo "Fertig. Alle lokalen Klone neu klonen (nicht pullen)."
