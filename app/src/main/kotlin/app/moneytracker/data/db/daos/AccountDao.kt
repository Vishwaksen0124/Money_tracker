package app.moneytracker.data.db.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import app.moneytracker.data.db.entities.AccountEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(account: AccountEntity)

    @Query("SELECT * FROM accounts")
    fun observeAll(): Flow<List<AccountEntity>>
}
