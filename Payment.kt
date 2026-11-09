package com.paymentannouncer.app

import androidx.room.*

@Entity(tableName = "payments", indices = [Index(value = ["fingerprint"], unique = true)])
data class Payment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amount: Double,
    val direction: String,
    val appName: String,
    val detail: String,
    val createdAt: Long,
    val fingerprint: String
)

@Dao
interface PaymentDao {
    @Query("SELECT * FROM payments ORDER BY createdAt DESC")
    fun observeAll(): kotlinx.coroutines.flow.Flow<List<Payment>>
    @Query("SELECT * FROM payments ORDER BY createdAt DESC")
    suspend fun all(): List<Payment>
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(payment: Payment): Long
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(items: List<Payment>)
    @Query("DELETE FROM payments") suspend fun clear()
    @Query("SELECT COALESCE(SUM(amount),0) FROM payments WHERE direction='RECEIVED'")
    fun received(): kotlinx.coroutines.flow.Flow<Double>
    @Query("SELECT COALESCE(SUM(amount),0) FROM payments WHERE direction='SENT'")
    fun sent(): kotlinx.coroutines.flow.Flow<Double>
}

@Database(entities = [Payment::class], version = 1, exportSchema = false)
abstract class PaymentDatabase : RoomDatabase() {
    abstract fun dao(): PaymentDao
    companion object {
        @Volatile private var INSTANCE: PaymentDatabase? = null
        fun get(context: android.content.Context): PaymentDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(context.applicationContext, PaymentDatabase::class.java, "payments.db").build().also { INSTANCE = it }
            }
    }
}
