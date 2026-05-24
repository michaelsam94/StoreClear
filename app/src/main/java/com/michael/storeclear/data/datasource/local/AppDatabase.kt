package com.michael.storeclear.data.datasource.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "hash_cache")
data class HashCacheEntity(
    @PrimaryKey val uriString: String,
    val hash: String,
    val algorithm: String,
    val lastModified: Long,
    val fileSize: Long,
    val computedAt: Long
)

@Entity(tableName = "shred_history")
data class ShredHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fileName: String,
    val fileSizeBefore: Long,
    val algorithm: String,
    val passCount: Int,
    val shredAt: Long
)

@Dao
interface StoreClearDao {
    @Query("SELECT * FROM hash_cache WHERE uriString = :uriString AND algorithm = :algorithm LIMIT 1")
    suspend fun getHashCache(uriString: String, algorithm: String): HashCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHashCache(entity: HashCacheEntity)

    @Query("DELETE FROM hash_cache")
    suspend fun clearHashCache()

    @Query("SELECT COUNT(*) FROM hash_cache")
    suspend fun getHashCacheCount(): Long

    @Query("SELECT * FROM shred_history ORDER BY shredAt DESC")
    fun getShredHistory(): Flow<List<ShredHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertShredHistory(entity: ShredHistoryEntity)
}

@Database(entities = [HashCacheEntity::class, ShredHistoryEntity::class], version = 1, exportSchema = false)
abstract class StoreClearDatabase : RoomDatabase() {
    abstract fun dao(): StoreClearDao
}
