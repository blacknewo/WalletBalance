package jp.maki.wallettracker

import androidx.room.*
import kotlinx.coroutines.flow.Flow

// --- Entity ---
@Entity(tableName = "transactions")
data class Transaction(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String, // "income" or "expense"
    val amount: Int,
    val memo: String,
    val createdAt: Long = System.currentTimeMillis()
)

// --- DAO ---
@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions ORDER BY createdAt DESC")
    fun getAllFlow(): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions ORDER BY createdAt DESC LIMIT 10")
    fun getRecentFlow(): Flow<List<Transaction>>

    @Insert
    suspend fun insert(transaction: Transaction): Long

    @Update
    suspend fun update(transaction: Transaction): Int

    @Delete
    suspend fun delete(transaction: Transaction): Int

    @Query("DELETE FROM transactions")
    suspend fun deleteAll()

    @Query("SELECT SUM(CASE WHEN type = 'income' THEN amount ELSE -amount END) FROM transactions")
    fun getBalanceFlow(): Flow<Int?>
}

// --- Database ---
@Database(entities = [Transaction::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: android.content.Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "wallet_db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
