package sg.org.bcc.attendance.util

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import io.github.z4kn4fein.semver.toVersion
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.contentLength
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.readAvailable
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream

@Serializable
data class GitHubRelease(
    @SerialName("tag_name") val tagName: String,
    val assets: List<GitHubAsset>,
    val prerelease: Boolean
)

@Serializable
data class GitHubAsset(
    val name: String,
    @SerialName("browser_download_url") val downloadUrl: String
)

sealed class AppUpdate {
    data object UpToDate : AppUpdate()
    data class VersionsAvailable(
        val mainline: NewVersion? = null,
        val beta: NewVersion? = null
    ) : AppUpdate()
    data class Error(val message: String) : AppUpdate()

    data class NewVersion(val version: String, val downloadUrl: String, val isBeta: Boolean)
}

class AppUpdateManager(private val context: Context) {

    private val client = HttpClient {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
            })
        }
    }

    private val releasesUrl = "https://api.github.com/repos/bccsg/attendance-app/releases"

    suspend fun checkForUpdates(): AppUpdate {
        return try {
            val releases: List<GitHubRelease> = client.get(releasesUrl).body()
            val currentVersionStr = getAppVersion()
            val currentVersion = currentVersionStr.toVersion(strict = false)

            val latestMainline = releases.firstOrNull { !it.prerelease }
            val latestBeta = releases.firstOrNull { it.prerelease }

            val mainlineUpdate = latestMainline?.let { release ->
                val versionStr = release.tagName.removePrefix("v")
                val version = versionStr.toVersion(strict = false)
                if (version > currentVersion) {
                    val apkAsset = release.assets.find { it.name.endsWith(".apk") }
                    apkAsset?.let { AppUpdate.NewVersion(versionStr, it.downloadUrl, false) }
                } else null
            }

            val betaUpdate = latestBeta?.let { release ->
                val versionStr = release.tagName.removePrefix("v")
                val version = versionStr.toVersion(strict = false)
                if (version > currentVersion) {
                    val apkAsset = release.assets.find { it.name.endsWith(".apk") }
                    apkAsset?.let { AppUpdate.NewVersion(versionStr, it.downloadUrl, true) }
                } else null
            }

            if (mainlineUpdate != null || betaUpdate != null) {
                AppUpdate.VersionsAvailable(mainlineUpdate, betaUpdate)
            } else {
                AppUpdate.UpToDate
            }
        } catch (e: Exception) {
            AppUpdate.Error(e.message ?: "Unknown error")
        }
    }

    fun getAppVersion(): String {
        return try {
            val pInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
            pInfo.versionName ?: "0.0.0"
        } catch (e: Exception) {
            "0.0.0"
        }
    }

    fun isCurrentVersionBeta(): Boolean {
        return try {
            getAppVersion().toVersion(strict = false).isPreRelease
        } catch (e: Exception) {
            false
        }
    }

    suspend fun downloadApk(url: String, onProgress: (Long, Long) -> Unit): File? {
        if (url == "https://example.com/mock.apk") {
            return mockDownloadCurrentApk(onProgress)
        }
        return try {
            val file = File(context.cacheDir, "update.apk")
            
            client.prepareGet(url).execute { response ->
                val channel = response.bodyAsChannel()
                val contentLength = response.contentLength() ?: -1L
                var totalBytesRead = 0L
                val buffer = ByteArray(8192)
                
                FileOutputStream(file).use { output ->
                    while (true) {
                        val read = channel.readAvailable(buffer)
                        if (read == -1) break
                        if (read > 0) {
                            output.write(buffer, 0, read)
                            totalBytesRead += read
                            onProgress(totalBytesRead, contentLength)
                        }
                    }
                }
            }
            file
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun mockDownloadCurrentApk(onProgress: (Long, Long) -> Unit): File? {
        return try {
            val sourceFile = File(context.applicationInfo.sourceDir)
            val targetFile = File(context.cacheDir, "update.apk")
            val totalBytes = sourceFile.length()
            var bytesRead = 0L

            sourceFile.inputStream().use { input ->
                targetFile.outputStream().use { output ->
                    // Larger buffer to speed up simulation (256 KB)
                    val buffer = ByteArray(256 * 1024)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        bytesRead += read
                        onProgress(bytesRead, totalBytes)
                        // Adjust delay to reach ~5 seconds total.
                        // Assuming ~60MB APK and 256KB buffer -> ~240 reads.
                        // 5000ms / 240 ≈ 20ms delay.
                        kotlinx.coroutines.delay(20)
                    }
                }
            }
            targetFile
        } catch (e: Exception) {
            null
        }
    }
}
