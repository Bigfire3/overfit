package app.overfit.ui.photos

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
) {
    val t = LocalStrings.current
    val p = t.photos
    var selecting by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(listOf<String>()) }
    val photoList = photos.orEmpty()
    val historyList = history.orEmpty()
    val days = remember(photos, history) { buildDiaryDays(photoList, historyList) }
    val weekStart = DiaryCalendar.weekStart(language)
    val months = remember(days, today) { DiaryCalendar.months(days.keys.minOrNull(), today) }
    val streak = remember(days, today, weekStart) {
        DiaryCalendar.streakWeeks(days.values.filter { it.workouts.isNotEmpty() }.map { it.date }, today, weekStart)
    }
    val valid = selected.filter { id -> photoList.any { it.photo.id == id } }
    val loaded = photos != null && history != null
    val empty = loaded && photoList.isEmpty() && historyList.isEmpty()

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
            } else if (photoList.size >= 2) {
                SmallButton(p.compare, onClick = { selecting = true })
            }
        }
        Spacer(Modifier.height(Space.s))
        if (empty) {
            // The calendar stays visible even without a single photo or workout.
            Text(t.photosEmpty, tone = Tone.Muted)
            Spacer(Modifier.height(Space.xs))
            Text(p.emptyHint, style = Theme.type.caption, tone = Tone.Muted)
            Spacer(Modifier.height(Space.s))
        }
        if (loaded) {
            DiaryStats(historyList.size, photoList.size, streak)
            if (days.values.any { it.sauna }) DiaryLegend()
            Spacer(Modifier.height(Space.s))
            WeekdayRow(weekStart)
            Hairline()
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
            if (loaded) items(months, key = { "${it.first}-${it.second}" }) { (year, month) ->
                MonthBlock(year, month, weekStart, days, today, valid, selecting) { date, day ->
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

/** Filled dot = workout, ring = sauna. Only shown once a sauna day exists. */
@Composable
private fun DiaryLegend() {
    val p = LocalStrings.current.photos
    Row(Modifier.fillMaxWidth().padding(bottom = Space.xs), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).background(Theme.colors.foreground, CircleShape))
        Spacer(Modifier.width(Space.xs))
        Text(p.legendWorkout, style = Theme.type.caption, tone = Tone.Muted)
        Spacer(Modifier.width(Space.m))
        SaunaRing(8.dp, 1.5.dp)
        Spacer(Modifier.width(Space.xs))
        Text(p.legendSauna, style = Theme.type.caption, tone = Tone.Muted)
    }
}

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
                        if (date != null) DayCell(date, days[date], date == today, date <= today, selected, selecting, onDay)
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
    onDay: (LocalDate, DiaryDay?) -> Unit,
) {
    val p = LocalStrings.current.photos
    val photo = day?.photos?.lastOrNull()
    val training = day != null && day.workouts.isNotEmpty()
    val sauna = day != null && day.sauna
    val desc = p.dayDescription(date.dayOfMonth, day?.photos?.size ?: 0, training, sauna)
    val shape = RoundedCornerShape(8.dp)
    val picked = photo != null && photo.photo.id in selected
    Column(
        Modifier
            .fillMaxWidth()
            .aspectRatio(0.62f)
            .then(if (isToday) Modifier.border(1.dp, Theme.colors.foreground, shape) else Modifier)
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
        Spacer(Modifier.height(2.dp))
        when {
            photo != null -> Box(Modifier.weight(1f).fillMaxWidth().clip(shape).background(Theme.colors.surface)) {
                AsyncImage(photo.photo.path, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                val n = day!!.photos.size
                if (n > 1) Box(
                    Modifier.align(Alignment.BottomEnd).padding(4.dp).size(20.dp).background(Theme.colors.inverseBackground, CircleShape),
                    contentAlignment = Alignment.Center,
                ) { Text(p.photoCount(n), style = Theme.type.caption, tone = Tone.Inverse) }
                if (selecting && picked) Box(Modifier.fillMaxSize().border(2.dp, Theme.colors.foreground, shape))
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
                HeaderDeleteButton(p.deletePhoto, p.deletePhotoConfirm, onConfirm = { onDelete(id) }, color = white)
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
    val sessionId = item.photo.sessionId
    val row = history?.firstOrNull { it.id == sessionId }
    val summary by produceState<WorkoutSummary?>(null, sessionId) { value = sessionId?.let { loadSummary(it) } }
    Column(Modifier.fillMaxWidth().padding(horizontal = Space.l, vertical = Space.m)) {
        BasicText(t.date(dateOf(item.photo.takenAt)), style = Theme.type.label.copy(color = white))
        // A photo of a day without a training has no workout line and no "view workout".
        item.workoutName?.let { BasicText(p.viewerWorkout(it), style = Theme.type.caption.copy(color = dim)) }
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
        if (sessionId != null && item.workoutName != null) {
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
        val days = dateOf(a.photo.takenAt).daysUntil(dateOf(b.photo.takenAt))
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
    val date = t.date(dateOf(item.photo.takenAt))
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
