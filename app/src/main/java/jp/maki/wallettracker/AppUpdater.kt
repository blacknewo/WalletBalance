package jp.maki.wallettracker

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class AppReleaseInfo(
    val tagName: String,         // 例: "v1.2.0"
    val versionName: String,     // 例: "1.2.0"
    val releaseNotes: String,    // リリースの説明文
    val apkDownloadUrl: String?  // APK ファイルの直接ダウンロード URL
)

object AppUpdater {
    private const val GITHUB_OWNER = "blacknewo"
    private const val GITHUB_REPO = "WalletBalance"
    private const val API_URL = "https://api.github.com/repos/$GITHUB_OWNER/$GITHUB_REPO/releases/latest"

    /**
     * GitHub Releases から最新バージョン情報を取得する。
     * 新バージョンがある場合のみ AppReleaseInfo を返す。最新なら null。
     */
    suspend fun checkForUpdate(currentVersionName: String): AppReleaseInfo? {
        return withContext(Dispatchers.IO) {
            try {
                val url = URL(API_URL)
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    setRequestProperty("User-Agent", "WalletBalance-App")
                    setRequestProperty("Accept", "application/vnd.github.v3+json")
                    connectTimeout = 8000
                    readTimeout = 8000
                }

                if (conn.responseCode != 200) {
                    return@withContext null
                }

                val response = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(response)
                val tagName = json.optString("tag_name", "").trim()
                val body = json.optString("body", "最新バージョンが公開されました。")
                val cleanLatestVersion = tagName.removePrefix("v").removePrefix("V")

                if (cleanLatestVersion.isEmpty()) return@withContext null

                // バージョン比較 (最新版 > 現在版)
                if (isNewerVersion(cleanLatestVersion, currentVersionName)) {
                    // assets の中から .apk ファイルを探す
                    var apkUrl: String? = null
                    val assets = json.optJSONArray("assets")
                    if (assets != null) {
                        for (i in 0 until assets.length()) {
                            val asset = assets.getJSONObject(i)
                            val name = asset.optString("name", "")
                            if (name.endsWith(".apk", ignoreCase = true)) {
                                apkUrl = asset.optString("browser_download_url", null)
                                break
                            }
                        }
                    }

                    return@withContext AppReleaseInfo(
                        tagName = tagName,
                        versionName = cleanLatestVersion,
                        releaseNotes = body,
                        apkDownloadUrl = apkUrl
                    )
                }

                null
            } catch (e: Exception) {
                null
            }
        }
    }

    /**
     * セマンティックバージョニング簡易比較 ("1.2.0" vs "1.1.0")
     */
    fun isNewerVersion(latest: String, current: String): Boolean {
        val latestParts = latest.split(".").mapNotNull { it.toIntOrNull() }
        val currentParts = current.split(".").mapNotNull { it.toIntOrNull() }

        val length = maxOf(latestParts.size, currentParts.size)
        for (i in 0 until length) {
            val l = latestParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (l > c) return true
            if (l < c) return false
        }
        return false
    }

    /**
     * DownloadManager を使って APK をダウンロードし、完了時に自動でインストーラを呼び出す
     */
    fun startDownloadAndInstall(context: Context, downloadUrl: String, versionName: String) {
        val fileName = "WalletBalance_v$versionName.apk"
        val destinationFile = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), fileName)
        if (destinationFile.exists()) {
            destinationFile.delete()
        }

        val request = DownloadManager.Request(Uri.parse(downloadUrl)).apply {
            setTitle("まきちゃんのお小遣い帳 アップデート")
            setDescription("v$versionName をダウンロード中...")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationUri(Uri.fromFile(destinationFile))
            setMimeType("application/vnd.android.package-archive")
        }

        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val downloadId = downloadManager.enqueue(request)

        val onComplete = object : BroadcastReceiver() {
            override fun onReceive(recvContext: Context?, intent: Intent?) {
                val id = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
                if (id == downloadId) {
                    try {
                        context.unregisterReceiver(this)
                    } catch (_: Exception) {}

                    installApk(context, destinationFile)
                }
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(
                onComplete,
                IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                Context.RECEIVER_EXPORTED
            )
        } else {
            context.registerReceiver(
                onComplete,
                IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
            )
        }
    }

    /**
     * FileProvider を介して APK インストール画面を起動する
     */
    fun installApk(context: Context, apkFile: File) {
        if (!apkFile.exists()) return

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
