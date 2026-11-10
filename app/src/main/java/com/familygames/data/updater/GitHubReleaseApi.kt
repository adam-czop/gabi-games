package com.familygames.data.updater

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import kotlin.coroutines.coroutineContext

@Serializable
data class ReleaseAsset(
    val name: String,
    val size: Long = 0,
    @SerialName("browser_download_url") val downloadUrl: String,
)

@Serializable
data class LatestRelease(
    @SerialName("tag_name") val tag: String,
    val assets: List<ReleaseAsset> = emptyList(),
)

sealed interface ReleaseResult {
    data class Found(val release: LatestRelease, val etag: String?) : ReleaseResult
    data object NotModified : ReleaseResult
    data object NoRelease : ReleaseResult
}

/** GitHub Releases API (docs/07 §2): public releases only, no tokens in the APK. */
class GitHubReleaseApi(
    private val client: OkHttpClient,
    private val owner: String,
    private val repo: String,
    private val userAgent: String,
    private val baseUrl: HttpUrl = "https://api.github.com/".toHttpUrl(),
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun latestRelease(etag: String?): ReleaseResult = withContext(Dispatchers.IO) {
        val url = baseUrl.newBuilder().addPathSegments("repos/$owner/$repo/releases/latest").build()
        val request = Request.Builder().url(url)
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("User-Agent", userAgent)
            .apply { if (etag != null) header("If-None-Match", etag) }
            .build()
        client.newCall(request).execute().use { response ->
            when (response.code) {
                200 -> ReleaseResult.Found(json.decodeFromString(LatestRelease.serializer(), response.body.string()), response.header("ETag"))
                304 -> ReleaseResult.NotModified
                404 -> ReleaseResult.NoRelease
                else -> throw IOException("GitHub API HTTP ${response.code}")
            }
        }
    }

    suspend fun downloadText(url: String): String = withContext(Dispatchers.IO) {
        client.newCall(Request.Builder().url(url).header("User-Agent", userAgent).build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code} for $url")
            response.body.string()
        }
    }

    /** Streams [url] into `target.part` and renames it when complete; reports progress in bytes. */
    suspend fun download(url: String, target: File, onProgress: (Long) -> Unit) = withContext(Dispatchers.IO) {
        val part = File(target.parentFile, target.name + ".part")
        target.parentFile?.mkdirs()
        try {
            client.newCall(Request.Builder().url(url).header("User-Agent", userAgent).build()).execute().use { response ->
                if (!response.isSuccessful) throw IOException("HTTP ${response.code} for $url")
                response.body.byteStream().use { input ->
                    part.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var total = 0L
                        while (true) {
                            coroutineContext.ensureActive()
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            total += read
                            onProgress(total)
                        }
                    }
                }
            }
            if (!part.renameTo(target)) throw IOException("Cannot move downloaded file")
        } finally {
            part.delete()
        }
    }
}
