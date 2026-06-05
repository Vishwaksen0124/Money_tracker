package app.moneytracker.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import app.moneytracker.data.db.daos.AccountDao
import app.moneytracker.data.db.daos.TransactionDao
import app.moneytracker.data.db.entities.AccountEntity
import app.moneytracker.data.db.entities.TransactionEntity

@Database(
    entities = [
        TransactionEntity::class,
        AccountEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun transactions(): TransactionDao
    abstract fun accounts(): AccountDao
}
