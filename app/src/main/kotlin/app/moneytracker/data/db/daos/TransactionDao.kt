package app.moneytracker.data.db.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import app.moneytracker.data.db.entities.TransactionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(txn: TransactionEntity)

    @Query("SELECT * FROM transactions ORDER BY tsMillis DESC LIMIT :limit")
    fun observeRecent(limit: Int = 200): Flow<List<TransactionEntity>>

    @Query("SELECT COUNT(*) FROM transactions")
    suspend fun count(): Int

    @Query("SELECT * FROM transactions WHERE rawHash = :hash LIMIT 1")
    suspend fun findByHash(hash: String): TransactionEntity?
}
