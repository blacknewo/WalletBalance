package jp.maki.wallettracker

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import org.json.JSONArray
import org.json.JSONObject
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

// ==============================================================
// バックアップ / リストア ヘルパー関数
// ==============================================================

/**
 * Transaction リストを JSON 文字列に変換する。
 * 形式: [{"id":1, "type":"income", "amount":1000, "memo":"...", "createdAt":...}, ...]
 */
fun transactionsToJson(transactions: List<Transaction>): String {
    val array = JSONArray()
    for (tx in transactions) {
        val obj = JSONObject()
        obj.put("id", tx.id)
        obj.put("type", tx.type)
        obj.put("amount", tx.amount)
        obj.put("memo", tx.memo)
        obj.put("createdAt", tx.createdAt)
        array.put(obj)
    }
    return array.toString(2) // インデント付きで読みやすく
}

/**
 * JSON 文字列を Transaction リストに変換する。
 * パース失敗時は null を返す（呼び出し元でエラーハンドリング）。
 */
fun jsonToTransactions(json: String): List<Transaction>? {
    return try {
        val array = JSONArray(json)
        val list = mutableListOf<Transaction>()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            list.add(
                Transaction(
                    // id は Room が自動採番するため 0 を渡し、insert 時に新規 ID が振られる
                    id = 0,
                    type = obj.getString("type"),
                    amount = obj.getInt("amount"),
                    memo = obj.optString("memo", ""),
                    createdAt = obj.getLong("createdAt")
                )
            )
        }
        list
    } catch (e: Exception) {
        null
    }
}

/**
 * ダウンロードフォルダに MakiWallet.json を書き出す（バックアップ）。
 * Android 10 以上: MediaStore 経由。
 * Android 9 以下: 直接ファイルアクセス。
 * @return 成功時 true、失敗時 false
 */
fun performBackup(context: Context, jsonContent: String): Boolean {
    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10 (API 29) 以上: MediaStore を使用
            val resolver = context.contentResolver
            val fileName = "MakiWallet.json"

            // 既存の同名ファイルがあれば先に削除（上書き相当）
            val queryUri = MediaStore.Downloads.EXTERNAL_CONTENT_URI
            val selection = "${MediaStore.Downloads.DISPLAY_NAME} = ?"
            resolver.query(queryUri, arrayOf(MediaStore.Downloads._ID), selection, arrayOf(fileName), null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID))
                    resolver.delete(Uri.withAppendedPath(queryUri, id.toString()), null, null)
                }
            }

            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, "application/json")
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: return false
            resolver.openOutputStream(uri)?.use { stream ->
                stream.write(jsonContent.toByteArray(Charsets.UTF_8))
            }
        } else {
            // Android 9 (API 28) 以下: 直接ファイル操作
            @Suppress("DEPRECATION")
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!downloadsDir.exists()) downloadsDir.mkdirs()
            val file = java.io.File(downloadsDir, "MakiWallet.json")
            file.writeText(jsonContent, Charsets.UTF_8)
        }
        true
    } catch (e: Exception) {
        false
    }
}

/**
 * 選択された URI から JSON を読み込む（リストア）。
 * @return JSON 文字列、失敗時は null
 */
fun readJsonFromUri(context: Context, uri: Uri): String? {
    return try {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            stream.readBytes().toString(Charsets.UTF_8)
        }
    } catch (e: Exception) {
        null
    }
}

// ==============================================================
// 書式・日付ヘルパー関数
// ==============================================================

fun formatCurrency(amount: Int): String {
    return NumberFormat.getCurrencyInstance(Locale.JAPAN).format(amount)
}

fun formatDate(timestamp: Long): String {
    val date = Date(timestamp)
    val sdf = SimpleDateFormat("MM/dd HH:mm", Locale.JAPAN)
    return sdf.format(date)
}

fun formatYearMonth(timestamp: Long): String {
    val date = Date(timestamp)
    val sdf = SimpleDateFormat("yyyy年M月", Locale.JAPAN)
    return sdf.format(date)
}

fun formatDayTime(timestamp: Long): String {
    val date = Date(timestamp)
    val sdf = SimpleDateFormat("d日(E) HH:mm", Locale.JAPAN)
    return sdf.format(date)
}
