package com.singam.lionlibrary.domain.model

/** Pure watch-history rules shared by screens, so each decision has one tested definition. */
object WatchProgressPolicy {
    /**
     * The episode a show's primary action should open. Regular seasons are preferred; specials are
     * used only when a show has nothing else. Within that pool: the most recently watched
     * unfinished episode, else the first episode not completed, else the first episode. Episodes
     * keep the caller's season/episode order.
     */
    fun resumeEpisode(episodes: List<Episode>, progress: List<WatchProgress>): Episode? {
        val pool = episodes.filter { it.seasonNumber > 0 }.ifEmpty { episodes }
        val poolIds = pool.mapTo(hashSetOf()) { it.id }
        val completed = progress.filter { it.completed }.mapTo(hashSetOf()) { it.episodeId }
        return progress
            .filter { !it.completed && it.lastPositionMs > 0 && it.episodeId in poolIds }
            .maxByOrNull { it.lastWatched }
            ?.let { record -> pool.find { it.id == record.episodeId } }
            ?: pool.firstOrNull { it.id !in completed }
            ?: pool.firstOrNull()
    }
}
