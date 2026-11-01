package app.aaps.database.daos

import androidx.room.Dao
import androidx.room.Query
import app.aaps.database.entities.TABLE_TEMPORARY_TARGETS
import app.aaps.database.entities.TemporaryTarget

@Dao
internal interface TemporaryTargetDao : TraceableDao<TemporaryTarget> {

    @Query("SELECT * FROM $TABLE_TEMPORARY_TARGETS WHERE id = :id")
    override suspend fun findById(id: Long): TemporaryTarget?

    @Query("DELETE FROM $TABLE_TEMPORARY_TARGETS")
    override suspend fun deleteAllEntries()

    @Query("DELETE FROM $TABLE_TEMPORARY_TARGETS WHERE timestamp < :than")
    override suspend fun deleteOlderThan(than: Long): Int

    @Query("DELETE FROM $TABLE_TEMPORARY_TARGETS WHERE referenceId IS NOT NULL")
    override suspend fun deleteTrackedChanges(): Int

    @Query("SELECT id FROM $TABLE_TEMPORARY_TARGETS ORDER BY id DESC limit 1")
    suspend fun getLastId(): Long?

    @Query("SELECT * FROM $TABLE_TEMPORARY_TARGETS WHERE (nightscoutId = :nsId) AND (referenceId IS NULL)")
    suspend fun findByNSId(nsId: String): TemporaryTarget?

    @Query("SELECT * FROM $TABLE_TEMPORARY_TARGETS WHERE (+timestamp <= :timestamp) AND ((timestamp + duration) > :timestamp) AND (+referenceId IS NULL) AND (isValid = 1) ORDER BY timestamp DESC LIMIT 1")
    suspend fun getTemporaryTargetActiveAtLegacy(timestamp: Long): TemporaryTarget?

    @Query("SELECT * FROM $TABLE_TEMPORARY_TARGETS WHERE (+timestamp <= :timestamp) AND ((timestamp + duration) > :timestamp) AND (+referenceId IS NULL) AND (isValid = 1) ORDER BY timestamp DESC LIMIT 1")
    suspend fun getTemporaryTargetActiveAt(timestamp: Long): TemporaryTarget?

    // Modified for Eating Now
    // Count EN TempTargets since EN start time
    @Query("SELECT COUNT(*) FROM $TABLE_TEMPORARY_TARGETS WHERE timestamp >= :timestamp AND isValid = 1 AND referenceId IS NULL AND (reason = :reason1 OR reason = :reason2)")
    suspend fun getENTemporaryTargetCountFromTime(timestamp: Long, reason1: TemporaryTarget.Reason, reason2: TemporaryTarget.Reason): Int

    // EATING NOW TT from time
    @Query("SELECT * FROM $TABLE_TEMPORARY_TARGETS WHERE timestamp >= :timestamp AND isValid = 1 AND referenceId IS NULL AND (reason = :reason1 OR reason = :reason2) ORDER BY timestamp ASC")
    suspend fun getENTemporaryTargetsFromTime(timestamp: Long, reason1: TemporaryTarget.Reason, reason2: TemporaryTarget.Reason): List<TemporaryTarget>

    // EATING NOW TT at time
    @Query("SELECT * FROM $TABLE_TEMPORARY_TARGETS WHERE timestamp <= :timestamp AND (timestamp + duration) > :timestamp AND (reason = :reason1 OR reason = :reason2) AND referenceId IS NULL AND isValid = 1 ORDER BY timestamp DESC LIMIT 1")
    suspend fun getENTemporaryTargetActiveAt(timestamp: Long, reason1: TemporaryTarget.Reason, reason2: TemporaryTarget.Reason): TemporaryTarget?

    /** Every entry running at [timestamp], also those that overlap; [getTemporaryTargetActiveAt] gives the last started of them. */
    @Query("SELECT * FROM $TABLE_TEMPORARY_TARGETS WHERE (+timestamp <= :timestamp) AND ((timestamp + duration) > :timestamp) AND (+referenceId IS NULL) AND (isValid = 1) ORDER BY +timestamp ASC")
    suspend fun getTemporaryTargetsActiveAt(timestamp: Long): List<TemporaryTarget>

    @Query("SELECT * FROM $TABLE_TEMPORARY_TARGETS WHERE (timestamp >= :timestamp) AND (isValid = 1) AND (+referenceId IS NULL) ORDER BY timestamp ASC")
    suspend fun getTemporaryTargetDataFromTime(timestamp: Long): List<TemporaryTarget>

    @Query("SELECT * FROM $TABLE_TEMPORARY_TARGETS WHERE (timestamp >= :timestamp) AND (+referenceId IS NULL) ORDER BY timestamp ASC")
    suspend fun getTemporaryTargetDataIncludingInvalidFromTime(timestamp: Long): List<TemporaryTarget>

    @Query("SELECT * FROM $TABLE_TEMPORARY_TARGETS WHERE id > :id ORDER BY id ASC limit 1")
    suspend fun getNextModifiedOrNewAfter(id: Long): TemporaryTarget?

    @Query("SELECT * FROM $TABLE_TEMPORARY_TARGETS WHERE id = :referenceId")
    suspend fun getCurrentFromHistoric(referenceId: Long): TemporaryTarget?

    @Query("SELECT * FROM $TABLE_TEMPORARY_TARGETS WHERE dateCreated > :since AND dateCreated <= :until LIMIT :limit OFFSET :offset")
    suspend fun getNewEntriesSince(since: Long, until: Long, limit: Int, offset: Int): List<TemporaryTarget>
}
