package com.singam.lionlibrary.domain.model

import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs

/**
 * Pure rules for turning chapters and provider answers into skip buttons. Platform-free so every
 * threshold is unit-tested; the data layer and player only apply these decisions.
 */
object SkipSegmentPolicy {
    /** Provider durations, end overshoot and terminal credits all use this tolerance. */
    const val DURATION_TOLERANCE_MS = 2_000L
    const val MIN_CONFIDENCE = 0.5
    const val MIN_INTRO_MS = 5_000L
    const val MAX_INTRO_MS = 300_000L
    const val MAX_INTRO_WINDOW_MS = 600_000L
    const val MAX_CREDITS_MS = 1_800_000L
    private const val MIN_CHAPTER_MS = 1_000L
    private const val MAX_LABEL_WORDS = 3

    private enum class ChapterKind(val type: SkipSegmentType, val rank: Int) {
        OPENING(SkipSegmentType.INTRO, 2),
        INTRO(SkipSegmentType.INTRO, 1),
        CREDITS(SkipSegmentType.CREDITS, 1),
    }

    private val openingWords = setOf("opening", "op")
    private val introWords = setOf("intro")
    private val creditWords = setOf("credits", "ending", "ed", "outro")
    // Labels such as "Ending Preview" or "OP Recap" describe something else.
    private val otherPartWords = setOf("preview", "pv", "recap", "next", "prologue")

    /**
     * Classifies a chapter label case-insensitively, ignoring numbering ("OP 1", "Opening 2",
     * "ED2"). Generic labels such as "Chapter 3" or "Episode" are not recognized.
     */
    fun chapterType(title: String?): SkipSegmentType? = kind(title)?.type

    private fun kind(title: String?): ChapterKind? {
        if (title.isNullOrBlank()) return null
        val words =
            Normalizer.normalize(title, Normalizer.Form.NFKC)
                .lowercase(Locale.ROOT)
                .split(Regex("[^\\p{L}\\p{N}]+"))
                .flatMap { it.split(Regex("(?<=\\p{L})(?=\\p{N})|(?<=\\p{N})(?=\\p{L})")) }
                .filter { it.isNotEmpty() && it.any(Char::isLetter) }
        if (words.isEmpty() || words.size > MAX_LABEL_WORDS) return null
        if (words.any { it in otherPartWords }) return null
        return when {
            words.any { it in openingWords } -> ChapterKind.OPENING
            words.any { it in creditWords } -> ChapterKind.CREDITS
            words.any { it in introWords } -> ChapterKind.INTRO
            else -> null
        }
    }

    /**
     * At most one intro and one credits segment from named chapters. An Opening/OP label outranks a
     * plain Intro (some groups label the cold open "Intro" and the title sequence "OP"). The
     * earliest intro and the last credits are used; adjacent chapters of the same kind merge.
     */
    fun fromChapters(chapters: List<PlaybackChapter>, durationMs: Long): List<SkipSegment> {
        val known = durationMs.takeIf { it > 0 }
        val sorted = chapters.filter { it.startMs >= 0 }.sortedBy { it.startMs }
        val spans = sorted.mapIndexedNotNull { index, chapter ->
            val kind = kind(chapter.title) ?: return@mapIndexedNotNull null
            val nextStart = sorted.getOrNull(index + 1)?.startMs
            val end =
                (chapter.endMs?.takeIf { it > chapter.startMs } ?: nextStart ?: known)?.let {
                    if (known != null) minOf(it, known) else it
                }
            if (known != null && chapter.startMs >= known) return@mapIndexedNotNull null
            if (end != null && end - chapter.startMs < MIN_CHAPTER_MS) return@mapIndexedNotNull null
            Triple(kind, chapter.startMs, end)
        }
        val intro =
            spans
                .filter { it.first.type == SkipSegmentType.INTRO }
                .let { intros ->
                    val best = intros.maxOfOrNull { it.first.rank }
                    merged(intros.filter { it.first.rank == best }).firstOrNull()
                }
                ?.takeIf { (start, end) -> end != null && isPlausibleChapter(start, end, known) }
        val credits = merged(spans.filter { it.first.type == SkipSegmentType.CREDITS }).lastOrNull()
        return listOfNotNull(
            intro?.let {
                SkipSegment(SkipSegmentType.INTRO, it.first, it.second, SkipSource.CHAPTER)
            },
            credits?.let {
                SkipSegment(SkipSegmentType.CREDITS, it.first, it.second, SkipSource.CHAPTER)
            },
        )
    }

    // A chapter covering most of the file is a mislabelled main part, not a skippable intro.
    private fun isPlausibleChapter(start: Long, end: Long, durationMs: Long?) =
        durationMs == null || end - start <= durationMs / 2

    private fun merged(spans: List<Triple<ChapterKind, Long, Long?>>): List<Pair<Long, Long?>> =
        spans.fold(mutableListOf()) { runs, (_, start, end) ->
            val last = runs.lastOrNull()
            if (last != null && last.second == start) runs[runs.lastIndex] = last.first to end
            else runs += start to end
            runs
        }

    /** TheIntroDB: enough agreement, and the matched release has the playing file's duration. */
    fun isTheIntroDbEligible(confidence: Double?, groupDurationMs: Long?, fileDurationMs: Long) =
        confidence != null &&
            confidence >= MIN_CONFIDENCE &&
            groupDurationMs != null &&
            fileDurationMs > 0 &&
            abs(groupDurationMs - fileDurationMs) <= DURATION_TOLERANCE_MS

    /** SkipDB: enough agreement and an exact, unshifted duration match. */
    fun isSkipDbEligible(confidence: Double?, match: String?, adjusted: Boolean?) =
        confidence != null && confidence >= MIN_CONFIDENCE && match == "exact" && adjusted == false

    /**
     * Validates one online interval against the file. A null intro start means the very beginning;
     * a null credits end means the end of the file (an open-ended manual button). Returns null for
     * reversed, out-of-range, "no segment" sentinels or implausible placement.
     */
    fun validateOnline(
        type: SkipSegmentType,
        startMs: Long?,
        endMs: Long?,
        durationMs: Long,
        source: SkipSource,
        confidence: Double?,
    ): SkipSegment? {
        if (durationMs <= 0) return null
        val start = startMs ?: if (type == SkipSegmentType.INTRO) 0L else return null
        if (start < 0 || start >= durationMs) return null
        val end = endMs?.let {
            when {
                it <= start -> return null
                it <= durationMs -> it
                it - durationMs <= DURATION_TOLERANCE_MS -> durationMs
                else -> return null
            }
        }
        return when (type) {
            SkipSegmentType.INTRO -> {
                end ?: return null
                val length = end - start
                val window = minOf(durationMs / 4, MAX_INTRO_WINDOW_MS)
                if (length < MIN_INTRO_MS || length > MAX_INTRO_MS || end > window) null
                else SkipSegment(type, start, end, source, confidence)
            }
            SkipSegmentType.CREDITS -> {
                val finalThird = durationMs - durationMs / 3
                if (start < finalThird || (end ?: durationMs) - start > MAX_CREDITS_MS) null
                else SkipSegment(type, start, end, source, confidence)
            }
        }
    }

    /**
     * One answer per type: named chapters, else TheIntroDB, else SkipDB. Within a source,
     * overlapping intervals keep the more confident one.
     */
    fun combine(chapters: List<SkipSegment>, online: List<SkipSegment>): List<SkipSegment> =
        SkipSegmentType.entries
            .flatMap { type ->
                val candidates = (chapters + online).filter { it.type == type }
                val source =
                    candidates.minOfOrNull { it.source.ordinal } ?: return@flatMap emptyList()
                candidates
                    .filter { it.source.ordinal == source }
                    .sortedByDescending { it.confidence ?: 1.0 }
                    .fold(mutableListOf<SkipSegment>()) { kept, segment ->
                        if (kept.none { overlaps(it, segment) }) kept += segment
                        kept
                    }
            }
            .sortedBy { it.startMs }

    private fun overlaps(a: SkipSegment, b: SkipSegment) =
        a.startMs < (b.endMs ?: Long.MAX_VALUE) && b.startMs < (a.endMs ?: Long.MAX_VALUE)

    /** The segment containing [positionMs]; the end is exclusive so a finished skip hides it. */
    fun active(segments: List<SkipSegment>, positionMs: Long, durationMs: Long): SkipSegment? =
        segments.firstOrNull { segment ->
            val end = segment.endMs ?: durationMs.takeIf { it > 0 } ?: Long.MAX_VALUE
            positionMs >= segment.startMs && positionMs < end
        }

    /** Credits that run to the end (or within the tolerance of it) finish the file when skipped. */
    fun isTerminal(segment: SkipSegment, durationMs: Long): Boolean =
        segment.type == SkipSegmentType.CREDITS &&
            (segment.endMs == null ||
                (durationMs > 0 && segment.endMs >= durationMs - DURATION_TOLERANCE_MS))

    /** Duration changes beyond the tolerance invalidate provider matches. */
    fun durationChanged(previousMs: Long, currentMs: Long) =
        abs(currentMs - previousMs) > DURATION_TOLERANCE_MS
}
