package com.apple.mail.data.db

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase

/** 缓存的邮件行。主键：账户 + 文件夹 + uidKey（uid 或本地 id）。 */
@Entity(tableName = "cached_emails", primaryKeys = ["accountId", "folder", "uidKey"])
data class CachedEmailEntity(
    val accountId: String,
    val folder: String,
    val uidKey: String,
    val emailId: Long,
    val uid: Long,
    val senderName: String,
    val senderEmail: String,
    val recipients: String,
    val subject: String,
    val preview: String,
    val body: String,
    val bodyHtml: String,
    val timestamp: Long,
    val category: String,
    val isRead: Boolean,
    val isFlagged: Boolean,
    val hasAttachment: Boolean,
    val attachmentName: String?,
    val attachmentNames: String,
    val priority: String,
    val avatarColorIndex: Int
)

/** 文件夹同步元数据（UID 水位）。 */
@Entity(tableName = "folder_meta", primaryKeys = ["accountId", "folder"])
data class FolderMetaEntity(
    val accountId: String,
    val folder: String,
    val uidValidity: Long,
    val maxUid: Long,
    val savedAt: Long
)

@Dao
interface MailCacheDao {

    @Query("SELECT * FROM folder_meta WHERE accountId = :accountId AND folder = :folder LIMIT 1")
    fun meta(accountId: String, folder: String): FolderMetaEntity?

    @Query("SELECT * FROM cached_emails WHERE accountId = :accountId AND folder = :folder ORDER BY timestamp DESC LIMIT :limit")
    fun emails(accountId: String, folder: String, limit: Int): List<CachedEmailEntity>

    @Query("DELETE FROM cached_emails WHERE accountId = :accountId AND folder = :folder")
    fun deleteEmails(accountId: String, folder: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertEmails(items: List<CachedEmailEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertMeta(meta: FolderMetaEntity)

    @Query("DELETE FROM cached_emails WHERE accountId = :accountId")
    fun deleteAccountEmails(accountId: String)

    @Query("DELETE FROM folder_meta WHERE accountId = :accountId")
    fun deleteAccountMeta(accountId: String)
}

@Database(
    entities = [CachedEmailEntity::class, FolderMetaEntity::class],
    version = 1,
    exportSchema = false
)
abstract class MailDatabase : RoomDatabase() {

    abstract fun cacheDao(): MailCacheDao

    companion object {
        @Volatile
        private var INSTANCE: MailDatabase? = null

        fun get(context: Context): MailDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    MailDatabase::class.java,
                    "mail_cache.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
