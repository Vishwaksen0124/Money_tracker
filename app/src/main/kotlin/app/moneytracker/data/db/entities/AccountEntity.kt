package app.moneytracker.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey val id: String,
    val bank: String,
    val last4: String,
    val displayName: String,
    val currentBalancePaise: Long?,
    val lastBalanceSeenAtMillis: Long?,
) {
    override fun toString(): String = "AccountEntity($id)"
}
