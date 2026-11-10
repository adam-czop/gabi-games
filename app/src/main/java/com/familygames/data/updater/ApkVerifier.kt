package com.familygames.data.updater

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import com.familygames.domain.update.UpdateInfo
import java.io.File
import java.security.MessageDigest

/** What the verifier needs to know about an APK file and about the installed app. */
data class ApkFacts(val packageName: String, val versionCode: Long, val signerSha256: Set<String>)

interface PackageArchiveReader {
    fun read(apk: File): ApkFacts?
    fun installed(): ApkFacts
}

class AndroidPackageArchiveReader(private val context: Context) : PackageArchiveReader {
    override fun read(apk: File): ApkFacts? {
        val info = context.packageManager.getPackageArchiveInfo(apk.path, PackageManager.GET_SIGNING_CERTIFICATES) ?: return null
        return info.toFacts()
    }

    override fun installed(): ApkFacts =
        context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES).toFacts()

    private fun PackageInfo.toFacts() = ApkFacts(
        packageName = packageName,
        versionCode = longVersionCode,
        signerSha256 = signingInfo?.apkContentsSigners.orEmpty().map { sha256(it.toByteArray()) }.toSet(),
    )
}

enum class VerifyError { Size, Hash, Unreadable, Package, Version, Signature, NotNewer }

/** docs/07 §4 / ADR-004: size, SHA-256, package, version and the same signing certificate as the installed app. */
class ApkVerifier(private val reader: PackageArchiveReader) {
    fun verify(apk: File, info: UpdateInfo): VerifyError? {
        if (apk.length() != info.apkSize) return VerifyError.Size
        if (!sha256(apk).equals(info.sha256, ignoreCase = true)) return VerifyError.Hash
        val archive = reader.read(apk) ?: return VerifyError.Unreadable
        val installed = reader.installed()
        return when {
            archive.packageName != installed.packageName -> VerifyError.Package
            archive.versionCode != info.versionCode -> VerifyError.Version
            archive.versionCode <= installed.versionCode -> VerifyError.NotNewer
            archive.signerSha256.isEmpty() || archive.signerSha256 != installed.signerSha256 -> VerifyError.Signature
            else -> null
        }
    }
}

internal fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).toHex()

internal fun sha256(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
        }
    }
    return digest.digest().toHex()
}

private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }
