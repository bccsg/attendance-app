package sg.org.bcc.attendance.util

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import io.github.z4kn4fein.semver.Version
import io.github.z4kn4fein.semver.toVersion
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.serialization.kotlinx.json.json
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

    suspend fun downloadApk(url: String, onProgress: (Float) -> Unit): File? {
        return try {
            val response = client.get(url)
            val file = File(context.cacheDir, "update.apk")
            val bytes = response.body<ByteArray>()
            FileOutputStream(file).use { it.write(bytes) }
            file
        } catch (e: Exception) {
            null
        }
    }
}
