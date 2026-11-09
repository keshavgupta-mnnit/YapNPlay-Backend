package com.kglabs28.yapnplay.backend.application.admin

/**
 * Read-only numbers for the dashboard's Analytics view, each within a
 * [StatsRange]. Buckets are labelled with the SQL `to_char` [format] of the
 * time in [zone] (one hour or one day). Never writes anything.
 */
interface AdminAnalyticsQueries {
    /** New accounts per bucket. */
    suspend fun newPlayers(range: StatsRange, zone: String, format: String): List<BucketCount>

    /** Different people who played a match that ended in each bucket. */
    suspend fun activePlayers(range: StatsRange, zone: String, format: String): List<BucketCount>

    /** Matches started in each bucket, by how they ended. */
    suspend fun matches(range: StatsRange, zone: String, format: String): List<BucketMatches>

    /** New accounts in [range] by where they were made. */
    suspend fun sources(range: StatsRange): List<SourceCount>

    /** Matches started in [range], per game, by how they ended. */
    suspend fun gameOutcomes(range: StatsRange): List<StoredGameOutcomes>

    /** Matches that ended in [range], per game: players, seats, quitters. */
    suspend fun gamePlayers(range: StatsRange): List<StoredGamePlayers>

    /** When the first account was made (null: none yet). */
    suspend fun firstAccountAt(): Long?
}
