package app.overfit.domain

import kotlin.math.min

/**
 * Unscharfe Suche über den Übungskatalog (≈1100 Einträge, läuft komplett im Speicher).
 *
 * - Alle Suchwörter müssen passen (UND). Ein Wort passt, wenn ein Namenswort gleich ist, damit
 *   beginnt, im zusammengeschriebenen Namen vorkommt ("pushup" ↔ "Push-up") oder sich nur um einen
 *   Tippfehler unterscheidet (ab 4 Zeichen, ab 8 Zeichen zwei).
 * - Deutsche Begriffe werden auf die englischen Katalognamen abgebildet ("Klimmzug" → "pull up").
 * - Deutsche Muskel- und Equipment-Bezeichnungen sind ebenfalls durchsuchbar ("Brust", "Kurzhantel").
 */
object ExerciseSearch {

    data class Filter(
        val query: String = "",
        val muscle: Muscle? = null,
        val equipment: Equipment? = null,
        val onlyCustom: Boolean = false,
    )

    /** Vorberechneter Suchindex für eine Übung. */
    class Indexed(val exercise: Exercise) {
        val nameTokens: List<String> = tokenize(exercise.name)
        val compactName: String = nameTokens.joinToString("")
        val tagTokens: List<String> = buildList {
            (exercise.primaryMuscles + exercise.secondaryMuscles).forEach { addAll(tokenize(it.label)) }
            exercise.equipment?.let { addAll(tokenize(it.label)) }
        }
    }

    fun index(exercises: List<Exercise>): List<Indexed> = exercises.map(::Indexed)

    fun search(index: List<Indexed>, filter: Filter): List<Exercise> {
        val candidates = index.filter { item ->
            val e = item.exercise
            (filter.muscle == null || filter.muscle in e.primaryMuscles) &&
                (filter.equipment == null || e.equipment == filter.equipment) &&
                (!filter.onlyCustom || e.isCustom)
        }
        val groups = parseQuery(filter.query)
        if (groups.isEmpty()) {
            return candidates.map { it.exercise }.sortedWith(defaultOrder)
        }
        val fullQuery = tokenize(filter.query).joinToString("")
        return candidates
            .mapNotNull { item -> score(item, groups, fullQuery)?.let { item.exercise to it } }
            .sortedWith { a, b ->
                val byScore = b.second.compareTo(a.second)
                if (byScore != 0) byScore else searchTieOrder.compare(a.first, b.first)
            }
            .map { it.first }
    }

    private val defaultOrder: Comparator<Exercise> =
        compareBy<Exercise> { sourceRank(it.source) }
            .thenBy { it.name.length > 40 }
            .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }

    /** Bei gleicher Trefferqualität: kürzere (spezifischere) Namen zuerst, „Pull-up“ vor „Assisted Pull-up“. */
    private val searchTieOrder: Comparator<Exercise> =
        compareBy<Exercise> { sourceRank(it.source) }
            .thenBy { it.name.length }
            .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }

    /** Eigene Übungen zuerst, dann Workout Guide (schönere Bilder), dann free-exercise-db. */
    private fun sourceRank(source: ExerciseSource) = when (source) {
        ExerciseSource.CUSTOM -> 0
        ExerciseSource.WORKOUT_GUIDE -> 1
        ExerciseSource.FREE_DB -> 2
    }

    /** Ein Suchwort = Alternativen (Original oder deutsches Synonym), jede Alternative = Wortfolge. */
    private fun parseQuery(query: String): List<List<List<String>>> =
        tokenize(query).map { token ->
            val alternatives = mutableListOf(listOf(token))
            GERMAN_ALIASES.forEach { (german, english) ->
                if (german == token || (token.length >= 4 && german.startsWith(token))) {
                    alternatives += english.split(' ')
                }
            }
            alternatives
        }

    private fun score(item: Indexed, groups: List<List<List<String>>>, fullQuery: String): Double? {
        var total = 0.0
        for (alternatives in groups) {
            val best = alternatives.maxOf { words ->
                var s = 0.0
                for (w in words) {
                    val ws = tokenScore(item, w)
                    if (ws <= 0.0) return@maxOf 0.0
                    s += ws
                }
                s / words.size
            }
            if (best <= 0.0) return null
            total += best
        }
        if (item.compactName == fullQuery) total += 5.0
        else if (item.compactName.startsWith(fullQuery)) total += 2.0
        return total
    }

    private fun tokenScore(item: Indexed, q: String): Double {
        var best = 0.0
        for (t in item.nameTokens) {
            val s = when {
                t == q -> 3.0
                t.startsWith(q) -> 2.0
                isTypo(q, t) -> 1.0
                else -> 0.0
            }
            if (s > best) best = s
        }
        if (best < 1.5 && q.length >= 3 && item.compactName.contains(q)) best = 1.5
        if (best == 0.0 && item.tagTokens.any { it.startsWith(q) }) best = 0.8
        return best
    }

    private fun isTypo(q: String, token: String): Boolean {
        if (q.length < 4) return false
        val allowed = if (q.length >= 8) 2 else 1
        // Gegen das ganze Wort und gegen einen gleich langen Wortanfang (Tippfehler beim Weitertippen).
        return distance(q, token) <= allowed ||
            (token.length > q.length && distance(q, token.take(q.length)) <= allowed)
    }

    /** Damerau-Levenshtein (optimal string alignment). */
    internal fun distance(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        val d = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) d[i][0] = i
        for (j in 0..b.length) d[0][j] = j
        for (i in 1..a.length) {
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                var v = min(min(d[i - 1][j] + 1, d[i][j - 1] + 1), d[i - 1][j - 1] + cost)
                if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) {
                    v = min(v, d[i - 2][j - 2] + 1)
                }
                d[i][j] = v
            }
        }
        return d[a.length][b.length]
    }

    /** Kleinschreibung, Umlaute ausschreiben, an Nicht-Buchstaben trennen, einfacher Plural. */
    internal fun tokenize(text: String): List<String> =
        text.lowercase()
            .replace("ä", "ae").replace("ö", "oe").replace("ü", "ue").replace("ß", "ss")
            .split(Regex("[^a-z0-9]+"))
            .filter { it.isNotEmpty() }
            .map { if (it.length > 3 && it.endsWith('s') && !it.endsWith("ss")) it.dropLast(1) else it }

    /** Deutsche Suchbegriffe → englische Wörter im Katalog (Schlüssel bereits normalisiert, Singular). */
    private val GERMAN_ALIASES: List<Pair<String, String>> = listOf(
        "bankdruecken" to "bench press",
        "schraegbank" to "incline",
        "klimmzug" to "pull up",
        "klimmzuege" to "pull up",
        "kinnzug" to "chin up",
        "kniebeuge" to "squat",
        "kniebeugen" to "squat",
        "kreuzheben" to "deadlift",
        "liegestuetz" to "push up",
        "liegestuetze" to "push up",
        "rudern" to "row",
        "ruderzug" to "row",
        "latzug" to "pulldown",
        "schulterdruecken" to "shoulder press",
        "ueberkopfdruecken" to "overhead press",
        "militaerpresse" to "military press",
        "seitheben" to "lateral raise",
        "frontheben" to "front raise",
        "ausfallschritt" to "lunge",
        "ausfallschritte" to "lunge",
        "beinpresse" to "leg press",
        "beinstrecker" to "leg extension",
        "beinbeuger" to "leg curl",
        "wadenheben" to "calf raise",
        "hueftstoss" to "hip thrust",
        "hueftheben" to "glute bridge",
        "beckenheben" to "glute bridge",
        "unterarmstuetz" to "plank",
        "seitstuetz" to "side plank",
        "stuetz" to "plank",
        "handstand" to "handstand",
        "dehnung" to "stretch",
        "dehnen" to "stretch",
        "fliegende" to "fly",
        "butterfly" to "fly",
        "bizepscurl" to "curl",
        "hammercurl" to "hammer curl",
        "trizepsdruecken" to "pushdown",
        "kabelzug" to "cable",
        "ueberzuege" to "pullover",
        "bauchpresse" to "crunch",
        "situp" to "sit up",
        "beinheben" to "leg raise",
        "russischer" to "russian",
        "schulterheben" to "shrug",
        "nackenheben" to "shrug",
        "aufrecht" to "upright",
        "springen" to "jump",
        "seilspringen" to "jump rope",
        "hampelmann" to "jumping jack",
        "bergsteiger" to "mountain climber",
        "brustpresse" to "chest press",
        "kurzhantel" to "dumbbell",
        "langhantel" to "barbell",
        "band" to "band",
    )
}
