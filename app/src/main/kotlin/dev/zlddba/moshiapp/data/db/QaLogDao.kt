package dev.zlddba.moshiapp.data.db

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query

@Dao
interface QaLogDao {

    @Insert
    suspend fun insert(log: QaLogEntity): Long

    @Query("SELECT * FROM qa_logs ORDER BY id DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<QaLogEntity>

    @Query("DELETE FROM qa_logs")
    suspend fun deleteAll()

    @Query(
        "DELETE FROM qa_logs WHERE id NOT IN " +
            "(SELECT id FROM qa_logs ORDER BY id DESC LIMIT :keep)"
    )
    suspend fun trim(keep: Int)
}
