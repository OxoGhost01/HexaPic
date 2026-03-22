package com.oxoghost.hexapic.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class ReleaseInfo(val versionName: String, val apkUrl: String)

object UpdateChecker {

    private const val API_URL =
        "https://api.github.com/repos/oxoghost/HexaPic/releases/latest"

    suspend fun check(currentVersion: String): ReleaseInfo? = withContext(Dispatchers.IO) {
        try {
            val conn = URL(API_URL).openConnection() as HttpURLConnection
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            conn.connectTimeout = 8_000
            conn.readTimeout = 8_000
            if (conn.responseCode != 200) return@withContext null

            val json = JSONObject(conn.inputStream.bufferedReader().readText())
            val tagName = json.getString("tag_name").trimStart('v')
            if (tagName == currentVersion) return@withContext null

            val assets = json.getJSONArray("assets")
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                if (asset.getString("name").endsWith(".apk")) {
                    return@withContext ReleaseInfo(
                        versionName = tagName,
                        apkUrl = asset.getString("browser_download_url")
                    )
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }
}
