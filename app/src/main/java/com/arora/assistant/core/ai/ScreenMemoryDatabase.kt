package com.arora.assistant.core.ai

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

data class ScreenMemoryItem(
    val id: Long = 0,
    val timestamp: Long,
    val packageName: String,
    val activityTitle: String,
    val ocrText: String,
    val thumbnailBytes: ByteArray?
) {
    fun getThumbnailBitmap(): Bitmap? {
        return thumbnailBytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
    }
}

class ScreenMemoryDatabase(context: Context) : SQLiteOpenHelper(context, "arora_screen_memory.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE screen_memory (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                timestamp INTEGER,
                package_name TEXT,
                activity_title TEXT,
                ocr_text TEXT,
                thumbnail BLOB
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_timestamp ON screen_memory(timestamp)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS screen_memory")
        onCreate(db)
    }

    suspend fun saveScreen(
        packageName: String,
        activityTitle: String,
        ocrText: String,
        bitmap: Bitmap?
    ) = withContext(Dispatchers.IO) {
        val thumbnailBytes = bitmap?.let {
            val thumb = Bitmap.createScaledBitmap(it, 160, (160f * it.height / it.width).toInt(), true)
            val stream = ByteArrayOutputStream()
            thumb.compress(Bitmap.CompressFormat.JPEG, 70, stream)
            stream.toByteArray()
        }

        val values = ContentValues().apply {
            put("timestamp", System.currentTimeMillis())
            put("package_name", packageName)
            put("activity_title", activityTitle)
            put("ocr_text", ocrText)
            put("thumbnail", thumbnailBytes)
        }

        writableDatabase.insert("screen_memory", null, values)

        // Keep rolling buffer limited to last 100 captured screens to preserve storage
        writableDatabase.execSQL("DELETE FROM screen_memory WHERE id NOT IN (SELECT id FROM screen_memory ORDER BY timestamp DESC LIMIT 100)")
    }

    suspend fun queryMemory(query: String, limit: Int = 5): List<ScreenMemoryItem> = withContext(Dispatchers.IO) {
        val list = mutableListOf<ScreenMemoryItem>()
        val words = query.lowercase().split("\\s+".toRegex()).filter { it.length > 2 }
        
        val whereClause = if (words.isNotEmpty()) {
            words.joinToString(" OR ") { "LOWER(ocr_text) LIKE '%$it%'" }
        } else {
            "1=1"
        }

        val cursor = readableDatabase.query(
            "screen_memory",
            null,
            whereClause,
            null,
            null,
            null,
            "timestamp DESC",
            limit.toString()
        )

        cursor.use {
            val idCol = it.getColumnIndexOrThrow("id")
            val timeCol = it.getColumnIndexOrThrow("timestamp")
            val pkgCol = it.getColumnIndexOrThrow("package_name")
            val titleCol = it.getColumnIndexOrThrow("activity_title")
            val textCol = it.getColumnIndexOrThrow("ocr_text")
            val thumbCol = it.getColumnIndexOrThrow("thumbnail")

            while (it.moveToNext()) {
                list.add(
                    ScreenMemoryItem(
                        id = it.getLong(idCol),
                        timestamp = it.getLong(timeCol),
                        packageName = it.getString(pkgCol),
                        activityTitle = it.getString(titleCol) ?: "",
                        ocrText = it.getString(textCol) ?: "",
                        thumbnailBytes = it.getBlob(thumbCol)
                    )
                )
            }
        }
        list
    }

    suspend fun getRecentTimeline(durationMillis: Long = 2 * 60 * 60 * 1000): List<ScreenMemoryItem> = withContext(Dispatchers.IO) {
        val list = mutableListOf<ScreenMemoryItem>()
        val since = System.currentTimeMillis() - durationMillis

        val cursor = readableDatabase.query(
            "screen_memory",
            null,
            "timestamp >= ?",
            arrayOf(since.toString()),
            null,
            null,
            "timestamp ASC",
            "50"
        )

        cursor.use {
            val idCol = it.getColumnIndexOrThrow("id")
            val timeCol = it.getColumnIndexOrThrow("timestamp")
            val pkgCol = it.getColumnIndexOrThrow("package_name")
            val titleCol = it.getColumnIndexOrThrow("activity_title")
            val textCol = it.getColumnIndexOrThrow("ocr_text")
            val thumbCol = it.getColumnIndexOrThrow("thumbnail")

            while (it.moveToNext()) {
                list.add(
                    ScreenMemoryItem(
                        id = it.getLong(idCol),
                        timestamp = it.getLong(timeCol),
                        packageName = it.getString(pkgCol),
                        activityTitle = it.getString(titleCol) ?: "",
                        ocrText = it.getString(textCol) ?: "",
                        thumbnailBytes = it.getBlob(thumbCol)
                    )
                )
            }
        }
        list
    }
}
