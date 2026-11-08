package app.overfit.ui.photos

import app.overfit.ui.components.FloatingCard
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.draw.alpha
import app.overfit.domain.WeightEntry
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.overfit.data.DiaryPhoto
import app.overfit.data.db.HistoryRow
import app.overfit.domain.DiaryCalendar
import app.overfit.domain.Language
import app.overfit.domain.WeightUnit
import app.overfit.domain.WorkoutSummary
import app.overfit.i18n.LocalStrings
import app.overfit.share.PlatformShare
import app.overfit.ui.components.BackHeader
import app.overfit.ui.components.HeaderDeleteButton
import app.overfit.ui.components.HeaderIconButton
import app.overfit.ui.components.Panel
import app.overfit.ui.components.panelRow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import app.overfit.ui.components.Hairline
import app.overfit.ui.components.Icon
import app.overfit.ui.components.LineIcon
import app.overfit.ui.components.PrimaryButton
import app.overfit.ui.components.Screen
import app.overfit.ui.components.Text
import app.overfit.ui.components.SmallButton
import app.overfit.ui.components.Title
import app.overfit.ui.components.Tone
import app.overfit.ui.history.formatVolume
import app.overfit.ui.history.recordText
import app.overfit.domain.Numbers
import app.overfit.ui.theme.Space
import app.overfit.ui.theme.Theme
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil

// ---------------------------------------------------------------------------------------------
// Diary tab: BeReal-style "Memories" — month by month calendar grid

/**
 * Calendar tab. [photos] and [history] are null while loading; both newest first. A tap on a day
 * (also one without anything, up to today) opens the day view with its photos and trainings.
 */
@Composable
fun PhotosScreen(
    photos: List<DiaryPhoto>?,
    history: List<HistoryRow>?,
    language: Language,
    today: LocalDate,
    onDay: (LocalDate) -> Unit,
    onCompare: (olderId: String, newerId: String) -> Unit,
    unit: WeightUnit = WeightUnit.KG,
    /** Start in the list view (remembered choice); [onListViewChange] stores each switch. */
    listView: Boolean = false,
    onListViewChange: (Boolean) -> Unit = {},
    /** Body-weight log (oldest first, null while loading) and a tap on the weight card (opens today's entry). */
    weights: List<WeightEntry>? = emptyList(),
    onWeightToday: () -> Unit = {},
    /** Opens the muscle map sheet (body icon in the title row); null hides the icon. */
    onMuscles: (() -> Unit)? = null,
) {
    val t = LocalStrings.current
    val p = t.photos
    var asList by remember { mutableStateOf(listView) }
    var selecting by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(listOf<String>()) }
    val photoList = photos.orEmpty()
    val historyList = history.orEmpty()
    val weightList = weights.orEmpty()
    val days = remember(photos, history, weights) { buildDiaryDays(photoList, historyList, weightList) }
    val weekStart = DiaryCalendar.weekStart(language)
    // Future months on top (newest first): the current month sits at index MONTHS_AHEAD, the list starts there.
    val months = remember(days, today) { DiaryCalendar.months(days.keys.filter { it <= today }.minOrNull(), today, DiaryCalendar.MONTHS_AHEAD) }
    val todayIndex = months.indexOf(today.year to today.monthNumber).coerceAtLeast(0)
    val gridState = rememberLazyListState(initialFirstVisibleItemIndex = todayIndex)
    val listState = rememberLazyListState()
    val jumpScope = rememberCoroutineScope()
    val streak = remember(days, today, weekStart) {
        DiaryCalendar.streakWeeks(days.values.filter { it.workouts.isNotEmpty() }.map { it.date }, today, weekStart)
    }
    val valid = selected.filter { id -> photoList.any { it.photo.id == id } }
    val loaded = photos != null && history != null
    // Calendar: the hint speaks of workouts and photos, a weight alone keeps it. List: weight days are rows, so not empty.
    val empty = loaded && photoList.isEmpty() && historyList.isEmpty() && (!asList || weightList.isEmpty())
    // "Today" only while away from it: grid not on the current month, list not at its top (newest day = today's end).
    val awayFromToday by remember(asList, todayIndex) {
        derivedStateOf {
            if (asList) listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0
            else gridState.firstVisibleItemIndex != todayIndex
        }
    }

    Screen(
        footer = if (selecting) {
            {
                if (valid.size < 2) Text(p.selectTwo, style = Theme.type.caption, tone = Tone.Muted)
                PrimaryButton(p.compare, enabled = valid.size == 2, onClick = {
                    val chosen = photoList.filter { it.photo.id in valid }.sortedBy { it.photo.takenAt }
                    if (chosen.size == 2) onCompare(chosen[0].photo.id, chosen[1].photo.id)
                })
            }
        } else null,
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Title(t.photosTitle)
            if (selecting) {
                SmallButton(p.cancel, onClick = { selecting = false; selected = emptyList() })
            } else {
                // Icons only (48 dp each), so the row fits at 360 dp: at most three of today / compare / muscles /
                // toggle at once – while "today" shows, compare waits (one tap back, or start comparing first and then
                // scroll: the selection stays).
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (loaded && awayFromToday) Box(Modifier.testTag(TODAY_BUTTON_TAG)) {
                        HeaderIconButton(Icon.Today, t.bodyWeight.jumpTodayDescription, {
                            jumpScope.launch { if (asList) listState.animateScrollToItem(0) else gridState.animateScrollToItem(todayIndex) }
                        })
                    }
                    if (!asList && photoList.size >= 2 && !(loaded && awayFromToday)) HeaderIconButton(Icon.Compare, p.compare, { selecting = true })
                    if (onMuscles != null) HeaderIconButton(Icon.Body, t.muscles.open, onMuscles)
                    // The icon shows the other view: list while the calendar is up, calendar while the list is up.
                    HeaderIconButton(
                        if (asList) Icon.Calendar else Icon.List,
                        if (asList) p.showCalendar else p.showList,
                        { asList = !asList; onListViewChange(asList) },
                    )
                }
            }
        }
        Spacer(Modifier.height(Space.s))
        if (weights != null && !selecting) {
            WeightCard(weightList, today, unit, language, onWeightToday)
            Spacer(Modifier.height(Space.s))
        }
        if (empty) {
            // The calendar stays visible even without a single photo or workout.
            Text(if (asList) p.listEmpty else t.photosEmpty, tone = Tone.Muted)
            Spacer(Modifier.height(Space.xs))
            Text(if (asList) p.listEmptyHint else p.emptyHint, style = Theme.type.caption, tone = Tone.Muted)
            Spacer(Modifier.height(Space.s))
        }
        if (loaded) {
            DiaryStats(historyList.size, photoList.size, streak)
            val anySauna = days.values.any { it.sauna }
            val anyWeight = days.values.any { it.weightKg != null }
            if (anySauna || anyWeight) DiaryLegend(anySauna, anyWeight)
            Spacer(Modifier.height(Space.s))
            if (!asList) {
                WeekdayRow(weekStart)
                Hairline()
            }
        }
        if (asList) {
            val listMonths = remember(days) { diaryListMonths(days) }
            LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("diary-list"), state = listState) {
                if (loaded) items(listMonths, key = { "${it.first.first}-${it.first.second}" }) { (ym, monthDays) ->
                    Text(p.monthHeader(ym.second, ym.first), Modifier.padding(top = Space.l, bottom = Space.s), style = Theme.type.title)
                    // One floating card per day.
                    Column(verticalArrangement = Arrangement.spacedBy(Space.m)) {
                        monthDays.forEach { DiaryListRow(it, unit, language) { onDay(it.date) } }
                    }
                }
            }
            return@Screen
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag(DIARY_GRID_TAG), state = gridState, verticalArrangement = Arrangement.spacedBy(Space.xs)) {
            if (loaded) items(months, key = { "${it.first}-${it.second}" }) { (year, month) ->
                MonthBlock(year, month, weekStart, days, today, valid, selecting, unit, language) { date, day ->
                    val last = day?.photos?.lastOrNull()
                    if (selecting) {
                        if (last != null) {
                            val id = last.photo.id
                            selected = when {
                                id in valid -> valid - id
                                valid.size < 2 -> valid + id
                                else -> listOf(valid[1], id)
                            }
                        }
                    } else {
                        onDay(date)
                    }
                }
            }
        }
    }
}

@Composable
private fun DiaryStats(workouts: Int, photos: Int, streakWeeks: Int) {
    val p = LocalStrings.current.photos
    Row(Modifier.fillMaxWidth().padding(vertical = Space.s), horizontalArrangement = Arrangement.spacedBy(Space.l)) {
        Text(p.statWorkouts(workouts), style = Theme.type.label)
        Text(p.statPhotos(photos), style = Theme.type.label)
        if (streakWeeks > 0) Text(p.statStreak(streakWeeks), style = Theme.type.label, tone = Tone.Muted)
    }
}

/** Filled dot = workout, ring = sauna, short dash = body weight. Only shown once a sauna or weight day exists. */
@Composable
private fun DiaryLegend(sauna: Boolean, weight: Boolean) {
    val p = LocalStrings.current.photos
    Row(Modifier.fillMaxWidth().padding(bottom = Space.xs), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).background(Theme.colors.foreground, CircleShape))
        Spacer(Modifier.width(Space.xs))
        Text(p.legendWorkout, style = Theme.type.caption, tone = Tone.Muted)
        if (sauna) {
            Spacer(Modifier.width(Space.m))
            SaunaRing(8.dp, 1.5.dp)
            Spacer(Modifier.width(Space.xs))
            Text(p.legendSauna, style = Theme.type.caption, tone = Tone.Muted)
        }
        if (weight) {
            Spacer(Modifier.width(Space.m))
            WeightDash()
            Spacer(Modifier.width(Space.xs))
            Text(LocalStrings.current.bodyWeight.legend, style = Theme.type.caption, tone = Tone.Muted)
        }
    }
}

/** Body-weight marker: a short muted dash under the day (no confusion with the workout dot or the sauna ring). */
@Composable
private fun WeightDash(modifier: Modifier = Modifier) {
    Box(modifier.size(width = 12.dp, height = 2.dp).background(Theme.colors.muted, RoundedCornerShape(1.dp)))
}

/** Test tags: weight dash in a calendar day, the month grid, the "Today" button. */
const val WEIGHT_MARKER_TAG = "diary-weight"
const val DIARY_GRID_TAG = "diary-grid"
const val TODAY_BUTTON_TAG = "diary-today"

/** Sauna marker: a hollow ring (the workout is the filled dot). [fill] keeps it readable on a photo. */
@Composable
private fun SaunaRing(size: androidx.compose.ui.unit.Dp, stroke: androidx.compose.ui.unit.Dp, color: Color = Theme.colors.foreground, fill: Color = Color.Transparent) {
    Box(Modifier.testTag(SAUNA_MARKER_TAG).size(size).background(fill, CircleShape).border(stroke, color, CircleShape))
}

/** Test tag of the sauna ring in a calendar day. */
const val SAUNA_MARKER_TAG = "diary-sauna"

@Composable
private fun WeekdayRow(weekStart: kotlinx.datetime.DayOfWeek) {
    val p = LocalStrings.current.photos
    Row(Modifier.fillMaxWidth().padding(bottom = Space.xs), horizontalArrangement = Arrangement.spacedBy(CELL_GAP)) {
        DiaryCalendar.weekdays(weekStart).forEach {
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Text(p.weekdaysShort[it.ordinal], style = Theme.type.caption, tone = Tone.Faint)
            }
        }
    }
}

private val CELL_GAP = 4.dp

@Composable
private fun MonthBlock(
    year: Int,
    month: Int,
    weekStart: kotlinx.datetime.DayOfWeek,
    days: Map<LocalDate, DiaryDay>,
    today: LocalDate,
    selected: List<String>,
    selecting: Boolean,
    unit: WeightUnit,
    language: Language,
    onDay: (LocalDate, DiaryDay?) -> Unit,
) {
    val p = LocalStrings.current.photos
    val cells = remember(year, month, weekStart) { DiaryCalendar.monthGrid(year, month, weekStart) }
    Column(Modifier.fillMaxWidth()) {
        Text(
            p.monthHeader(month, year),
            Modifier.padding(top = Space.l, bottom = Space.s),
            style = Theme.type.title,
        )
        cells.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth().padding(bottom = CELL_GAP), horizontalArrangement = Arrangement.spacedBy(CELL_GAP)) {
                week.forEach { date ->
                    Box(Modifier.weight(1f)) {
                        if (date != null) DayCell(date, days[date], date == today, date <= today, selected, selecting, unit, language, onDay)
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    day: DiaryDay?,
    isToday: Boolean,
    /** Days up to today open the day view (also empty ones, to add a photo); future days do nothing. */
    tappable: Boolean,
    selected: List<String>,
    selecting: Boolean,
    unit: WeightUnit,
    language: Language,
    onDay: (LocalDate, DiaryDay?) -> Unit,
) {
    val p = LocalStrings.current.photos
    val photo = day?.photos?.lastOrNull()
    val training = day != null && day.workouts.isNotEmpty()
    val sauna = day != null && day.sauna
    val weightKg = day?.weightKg
    val w = LocalStrings.current.bodyWeight
    val desc = p.dayDescription(date.dayOfMonth, day?.photos?.size ?: 0, training, sauna) +
        (weightKg?.let { w.dayDescription(app.overfit.domain.formatWeight(it, unit, language)) } ?: "")
    val shape = RoundedCornerShape(8.dp)
    val picked = photo != null && photo.photo.id in selected
    Column(
        Modifier
            .fillMaxWidth()
            .aspectRatio(0.62f)
            // Future days: greyed out and not tappable (nothing to log there yet).
            .then(if (!tappable) Modifier.alpha(0.5f) else Modifier)
            .then(if (isToday) Modifier.background(Theme.colors.surfacePressed, shape) else Modifier)
            .padding(2.dp)
            .semantics { contentDescription = desc }
            .then(if (if (selecting) photo != null else tappable) Modifier.clickable { onDay(date, day) } else Modifier),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            date.dayOfMonth.toString(),
            style = Theme.type.caption,
            tone = if (photo != null || training) Tone.Muted else Tone.Faint,
        )
        // Body weight: short dash right under the day number (belongs clearly to this day); the slot stays reserved
        // so all cells line up.
        Box(Modifier.fillMaxWidth().height(6.dp), contentAlignment = Alignment.Center) {
            if (weightKg != null) WeightDash(Modifier.testTag(WEIGHT_MARKER_TAG))
        }
        when {
            photo != null -> Box(Modifier.weight(1f).fillMaxWidth().clip(shape).background(Theme.colors.surface)) {
                AsyncImage(photo.photo.path, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                val n = day!!.photos.size
                if (n > 1) Box(
                    Modifier.align(Alignment.BottomEnd).padding(4.dp).size(20.dp).background(Theme.colors.inverseBackground, CircleShape),
                    contentAlignment = Alignment.Center,
                ) { Text(p.photoCount(n), style = Theme.type.caption, tone = Tone.Inverse) }
                if (selecting && picked) Box(Modifier.fillMaxSize().background(Theme.colors.foreground.copy(alpha = 0.35f)), contentAlignment = Alignment.Center) { LineIcon(Icon.Check, size = 24.dp) }
                // Photo and workout on the same day: the photo wins, a small dot keeps the workout visible.
                if (training) Row(Modifier.align(Alignment.TopStart).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    Box(Modifier.size(10.dp).background(Theme.colors.inverseBackground, CircleShape))
                    // Sauna: hollow ring next to the dot, filled with the page colour so it stays visible on the photo.
                    if (sauna) SaunaRing(10.dp, 2.dp, Theme.colors.inverseBackground, Theme.colors.background)
                }
            }
            // Workout without a photo: a clearly filled dot in the middle of the tile.
            // Sauna afterwards: a hollow ring below the dot.
            training -> Box(Modifier.weight(1f).fillMaxWidth().clip(shape).background(Theme.colors.surface), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(18.dp).background(Theme.colors.foreground, CircleShape))
                    if (sauna) {
                        Spacer(Modifier.height(4.dp))
                        SaunaRing(14.dp, 2.dp)
                    }
                }
            }
            else -> Spacer(Modifier.weight(1f))
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Viewer: full screen, swipe between days (horizontal) and between photos of a day (vertical)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PhotoViewerScreen(
    photos: List<DiaryPhoto>?,
    history: List<HistoryRow>?,
    photoId: String,
    language: Language,
    unit: WeightUnit,
    platform: PlatformShare,
    loadSummary: suspend (sessionId: String) -> WorkoutSummary?,
    loadJpeg: suspend (photoId: String) -> ByteArray?,
    onBack: () -> Unit,
    onWorkout: (sessionId: String) -> Unit,
    onDelete: (photoId: String) -> Unit,
) {
    val t = LocalStrings.current
    val p = t.photos
    val white = Color.White
    val body = Theme.type.body.copy(color = white)
    val list = photos.orEmpty()
    val days = remember(photos) { buildDiaryDays(list, emptyList()).values.filter { it.hasPhoto }.sortedBy { it.date } }
    val startDay = days.indexOfFirst { d -> d.photos.any { it.photo.id == photoId } }

    Column(Modifier.fillMaxSize().background(Color.Black).windowInsetsPadding(WindowInsets.safeDrawing)) {
        // Header like every detail screen: back left, share and delete as icons top right (for the photo shown).
        var shown by remember { mutableStateOf<String?>(null) }
        val scope = rememberCoroutineScope()
        Row(Modifier.padding(horizontal = Space.l, vertical = Space.m).height(44.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).semantics { contentDescription = t.back }.clickable(onClick = onBack),
                contentAlignment = Alignment.CenterStart,
            ) { LineIcon(Icon.Back, color = white) }
            Spacer(Modifier.weight(1f))
            shown?.let { id ->
                HeaderIconButton(Icon.Share, p.sharePhoto, {
                    scope.launch { loadJpeg(id)?.let { platform.shareBytes("overfit-$id.jpg", it, "image/jpeg") } }
                }, color = white)
                Spacer(Modifier.width(Space.xs))
                HeaderDeleteButton(p.deletePhoto, onConfirm = { onDelete(id) }, color = white)
            }
        }
        if (startDay < 0) {
            if (photos != null) BasicText(p.notFound, Modifier.padding(horizontal = Space.l), style = body)
            return@Column
        }
        val pager = rememberPagerState(initialPage = startDay) { days.size }
        // Index of the shown photo within each day (vertical pager), by day index.
        val inner = remember { mutableStateMapOf<Int, Int>() }
        val currentDay = days[pager.currentPage.coerceIn(0, days.lastIndex)]
        val innerIndex = (inner[pager.currentPage] ?: initialInner(days, startDay, pager.currentPage, photoId))
            .coerceIn(0, currentDay.photos.lastIndex)
        val item = currentDay.photos[innerIndex]
        SideEffect { shown = item.photo.id }

        HorizontalPager(pager, Modifier.fillMaxWidth().weight(1f), key = { days[it].date.toString() }) { page ->
            val day = days[page]
            if (day.photos.size == 1) {
                AsyncImage(day.photos[0].photo.path, p.photoDescription(t.date(day.date)), Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            } else {
                val v = rememberPagerState(initialPage = initialInner(days, startDay, page, photoId)) { day.photos.size }
                LaunchedEffect(v.currentPage) { inner[page] = v.currentPage }
                Box(Modifier.fillMaxSize()) {
                    VerticalPager(v, Modifier.fillMaxSize()) { i ->
                        AsyncImage(
                            day.photos[i].photo.path,
                            p.photoPage(i + 1, day.photos.size),
                            Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit,
                        )
                    }
                    Column(Modifier.align(Alignment.CenterEnd).padding(end = Space.s), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        repeat(day.photos.size) { i ->
                            Box(Modifier.size(6.dp).background(if (i == v.currentPage) white else white.copy(alpha = 0.35f), CircleShape))
                        }
                    }
                }
            }
        }
        ViewerInfo(item, history, language, unit, loadSummary, onWorkout)
    }
}

private fun initialInner(days: List<DiaryDay>, startDay: Int, page: Int, photoId: String): Int =
    if (page == startDay) days[page].photos.indexOfFirst { it.photo.id == photoId }.coerceAtLeast(0) else 0

@Composable
private fun ViewerInfo(
    item: DiaryPhoto,
    history: List<HistoryRow>?,
    language: Language,
    unit: WeightUnit,
    loadSummary: suspend (String) -> WorkoutSummary?,
    onWorkout: (String) -> Unit,
) {
    val t = LocalStrings.current
    val p = t.photos
    val h = t.history
    val white = Color.White
    val dim = white.copy(alpha = 0.6f)
    // Photos belong to their day (v13): the training is the old link, else the day's last training.
    val sessionId = item.photo.sessionId
        ?: history?.filter { dateOf(it.startedAt) == item.photo.date }?.maxByOrNull { it.startedAt }?.id
    val row = history?.firstOrNull { it.id == sessionId }
    val workoutName = item.workoutName ?: row?.name
    val summary by produceState<WorkoutSummary?>(null, sessionId) { value = sessionId?.let { loadSummary(it) } }
    Column(Modifier.fillMaxWidth().padding(horizontal = Space.l, vertical = Space.m)) {
        BasicText(t.date(item.photo.date), style = Theme.type.label.copy(color = white))
        // A photo of a day without a training has no workout line and no "view workout".
        workoutName?.let { BasicText(p.viewerWorkout(it), style = Theme.type.caption.copy(color = dim)) }
        val facts = buildList {
            val end = row?.endedAt
            if (row != null && end != null) add("${p.viewerDuration} ${Numbers.formatDuration(((end - row.startedAt) / 1000).toInt())}")
            val volume = row?.volumeKg ?: summary?.volumeKg ?: 0.0
            if (volume > 0) add("${h.volume} ${formatVolume(volume, unit, language)}")
        }
        if (facts.isNotEmpty()) BasicText(facts.joinToString(" · "), style = Theme.type.caption.copy(color = dim))
        summary?.records?.takeIf { it.isNotEmpty() }?.let { records ->
            Spacer(Modifier.height(Space.xs))
            BasicText(p.viewerBests, style = Theme.type.caption.copy(color = dim))
            records.take(3).forEach { BasicText(recordText(it, unit, language, t), style = Theme.type.caption.copy(color = white)) }
        }
        if (sessionId != null && workoutName != null) {
            Spacer(Modifier.height(Space.s))
            ViewerAction(p.toWorkout, white) { onWorkout(sessionId) }
        }
    }
}

@Composable
private fun ViewerAction(label: String, color: Color, onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.2f)))
    Box(Modifier.fillMaxWidth().height(48.dp).clickable(onClick = onClick), contentAlignment = Alignment.CenterStart) {
        BasicText(label, style = Theme.type.body.copy(color = color))
    }
}

// ---------------------------------------------------------------------------------------------
// Compare

@Composable
fun PhotoCompareScreen(photos: List<DiaryPhoto>?, firstId: String, secondId: String, onBack: () -> Unit) {
    val t = LocalStrings.current
    val p = t.photos
    val a = photos?.firstOrNull { it.photo.id == firstId }
    val b = photos?.firstOrNull { it.photo.id == secondId }
    Screen(scroll = true, header = { BackHeader(t.back, onBack) }) {
        if (a == null || b == null) {
            if (photos != null) Text(p.notFound, tone = Tone.Muted)
            return@Screen
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.s)) {
            ComparePane(a, Modifier.weight(1f))
            ComparePane(b, Modifier.weight(1f))
        }
        Spacer(Modifier.height(Space.l))
        Hairline()
        val days = a.photo.date.daysUntil(b.photo.date)
        Text(
            if (days == 0) p.sameDay else p.daysApart(kotlin.math.abs(days)),
            Modifier.padding(vertical = Space.m),
            tone = Tone.Muted,
        )
    }
}

@Composable
private fun ComparePane(item: DiaryPhoto, modifier: Modifier) {
    val t = LocalStrings.current
    val date = t.date(item.photo.date)
    Column(modifier) {
        AsyncImage(
            item.photo.path,
            t.photos.photoDescription(date),
            Modifier.fillMaxWidth().background(Theme.colors.hairline),
            contentScale = ContentScale.Fit,
        )
        Spacer(Modifier.height(Space.s))
        Text(date, style = Theme.type.caption, tone = Tone.Muted)
    }
}

/** One day in the list view: date, workout names, short numbers, photo thumbnail. A tap opens the day view. */
@Composable
private fun DiaryListRow(day: DiaryDay, unit: WeightUnit, language: Language, onClick: () -> Unit) {
    val p = LocalStrings.current.photos
    val h = LocalStrings.current.history
    val weekday = p.weekdaysShort[day.date.dayOfWeek.ordinal]
    val w = LocalStrings.current.bodyWeight
    val weight = day.weightKg?.let { app.overfit.domain.formatWeight(it, unit, language) }
    val title = when {
        day.workouts.isNotEmpty() -> day.workouts.joinToString(", ") { it.name }
        day.photos.isNotEmpty() -> p.statPhotos(day.photos.size)
        else -> w.title
    }
    val ms = day.workouts.sumOf { w -> w.endedAt?.let { it - w.startedAt } ?: 0L }
    val sets = day.workouts.sumOf { it.completedSets }
    val volume = day.workouts.sumOf { it.volumeKg }
    val meta = listOfNotNull(
        app.overfit.workout.WorkoutFormat.elapsed(ms).takeIf { ms > 0 && day.workouts.isNotEmpty() },
        h.setCount(sets).takeIf { day.workouts.isNotEmpty() },
        formatVolume(volume, unit, language).takeIf { volume > 0 },
        h.sauna.takeIf { day.sauna },
        // Weight-only day: the title already says "Body weight", the meta line is just the value.
        weight?.let { if (day.workouts.isEmpty() && day.photos.isEmpty()) it else w.listValue(it) },
    ).joinToString(" · ").ifEmpty { null }
    val desc = p.dayDescription(day.date.dayOfMonth, day.photos.size, day.workouts.isNotEmpty(), day.sauna) +
        (weight?.let { w.dayDescription(it) } ?: "")
    val photo = day.photos.lastOrNull()
    FloatingCard(Modifier.testTag("diary-list-row").semantics(mergeDescendants = true) { contentDescription = "$desc, $title" }, onClick = onClick) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(horizontal = Space.m, vertical = Space.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.widthIn(min = 36.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(day.date.dayOfMonth.toString(), style = Theme.type.label, maxLines = 1)
            Text(weekday, style = Theme.type.caption, tone = Tone.Muted, maxLines = 1)
        }
        Spacer(Modifier.width(Space.m))
        Column(Modifier.weight(1f)) {
            Text(title, style = Theme.type.label, maxLines = 2)
            if (meta != null) Text(meta, style = Theme.type.caption, tone = Tone.Muted, maxLines = 2)
        }
        if (photo != null) {
            Spacer(Modifier.width(Space.m))
            Box(Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).background(Theme.colors.surfacePressed)) {
                AsyncImage(photo.photo.path, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
        }
    }
    }
}
