# Mitmachen bei Overfit

Beiträge sind willkommen, du musst vorher nicht fragen.

1. Repo forken, Branch anlegen, ändern.
2. Lokal prüfen (siehe README, Abschnitt Bauen):
   `./gradlew :shared:testAndroidHostTest :shared:compileKotlinIosSimulatorArm64 :app:testDebugUnitTest`
3. Pull Request nach `main` öffnen und kurz beschreiben, was und warum.

Hinweise:
- Architektur, Designsystem (schwarz-weiß, nur Compose Foundation, Inter) und Regeln stehen in `CLAUDE.md`,
  Entscheidungen in `docs/DECISIONS.md`.
- Alle UI-Texte gehören in die Strings (Deutsch und Englisch).
- Mit dem Einreichen eines Beitrags stimmst du zu, dass er unter der MIT-Lizenz des Projekts steht.
- Sicherheitslücken bitte nicht als öffentliches Issue melden, sondern über GitHub „Report a vulnerability“
  (Security-Tab).
