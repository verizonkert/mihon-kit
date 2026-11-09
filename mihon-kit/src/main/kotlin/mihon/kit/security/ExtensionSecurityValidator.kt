package mihon.kit.security

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import java.io.File
import java.security.MessageDigest

/**
 * Result of cryptographic signature verification on an extension APK file.
 */
sealed interface SignatureVerificationResult {
    /**
     * APK's signature is valid and matches the trusted repository fingerprint.
     *
     * @property fingerprint Lowercase hex-encoded SHA-256 certificate fingerprint that matched.
     */
    data class Trusted(val fingerprint: String) : SignatureVerificationResult

    /**
     * APK signature is valid, but does not match the expected repository fingerprint.
     * Indicates a potentially modified, untrusted, or hijacked extension APK.
     *
     * @property actualFingerprint The signature fingerprint extracted from the APK.
     * @property expectedFingerprint The expected fingerprint declared by the repository.
     */
    data class Mismatch(val actualFingerprint: String, val expectedFingerprint: String) : SignatureVerificationResult

    /**
     * APK signature could not be extracted or verified (corrupted APK or unsupported signing scheme).
     *
     * @property message Descriptive failure explanation.
     */
    data class Error(val message: String) : SignatureVerificationResult
}

/**
 * Utilities for extracting and validating cryptographic SHA-256 certificate fingerprints
 * from extension APK files, matching Mihon's Extension Trust System.
 */
object ExtensionSecurityValidator {

    /**
     * Extracts the SHA-256 certificate fingerprint(s) from an uninstalled APK file.
     *
     * @param context Application context.
     * @param apkFile The extension APK file.
     * @return List of lowercase hex-encoded SHA-256 certificate fingerprints.
     */
    fun getApkSignatureFingerprints(context: Context, apkFile: File): List<String> {
        if (!apkFile.exists() || !apkFile.isFile) return emptyList()

        val pkgManager = context.packageManager
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            @Suppress("DEPRECATION")
            PackageManager.GET_SIGNATURES
        }

        val pkgInfo: PackageInfo = pkgManager.getPackageArchiveInfo(apkFile.absolutePath, flags)
            ?: return emptyList()

        val rawSignatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signingInfo = pkgInfo.signingInfo ?: return emptyList()
            if (signingInfo.hasMultipleSigners()) {
                signingInfo.apkContentsSigners
            } else {
                signingInfo.signingCertificateHistory
            }
        } else {
            @Suppress("DEPRECATION")
            pkgInfo.signatures
        } ?: return emptyList()

        return rawSignatures.mapNotNull { signature ->
            runCatching {
                val digest = MessageDigest.getInstance("SHA-256")
                val hashBytes = digest.digest(signature.toByteArray())
                hashBytes.joinToString("") { "%02x".format(it) }
            }.getOrNull()
        }
    }

    /**
     * Extracts the primary lowercase hex-encoded SHA-256 certificate fingerprint from an installed application package.
     *
     * @param context Application context.
     * @param packageName Android application package ID (e.g., `eu.kanade.tachiyomi.extension.all.mangadex`).
     * @return Hex-encoded SHA-256 fingerprint, or null if the package is not installed or signing certificates cannot be read.
     */
    fun getInstalledPackageFingerprint(context: Context, packageName: String): String? {
        val pkgManager = context.packageManager
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            @Suppress("DEPRECATION")
            PackageManager.GET_SIGNATURES
        }

        val pkgInfo = runCatching { pkgManager.getPackageInfo(packageName, flags) }.getOrNull()
            ?: return null

        val rawSignatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signingInfo = pkgInfo.signingInfo ?: return null
            if (signingInfo.hasMultipleSigners()) {
                signingInfo.apkContentsSigners
            } else {
                signingInfo.signingCertificateHistory
            }
        } else {
            @Suppress("DEPRECATION")
            pkgInfo.signatures
        } ?: return null

        return rawSignatures.firstOrNull()?.let { signature ->
            val digest = MessageDigest.getInstance("SHA-256")
            val hashBytes = digest.digest(signature.toByteArray())
            hashBytes.joinToString("") { "%02x".format(it) }
        }
    }

    /**
     * Verifies that the APK's certificate fingerprint matches the expected repository fingerprint.
     *
     * If [expectedFingerprint] is null or blank, verification succeeds as [SignatureVerificationResult.Trusted]
     * as long as the APK contains a valid signing certificate.
     *
     * @param context Application context.
     * @param apkFile The extension APK file to verify.
     * @param expectedFingerprint Hex-encoded SHA-256 fingerprint from repo.json, or null.
     * @return [SignatureVerificationResult.Trusted], [SignatureVerificationResult.Mismatch], or [SignatureVerificationResult.Error].
     */
    fun verifyApk(
        context: Context,
        apkFile: File,
        expectedFingerprint: String?,
    ): SignatureVerificationResult {
        val actualFingerprints = getApkSignatureFingerprints(context, apkFile)
        if (actualFingerprints.isEmpty()) {
            return SignatureVerificationResult.Error("No signing certificates found in APK: ${apkFile.name}")
        }

        if (expectedFingerprint.isNullOrBlank()) {
            // No expected fingerprint specified; valid signature exists
            return SignatureVerificationResult.Trusted(actualFingerprints.first())
        }

        val cleanExpected = expectedFingerprint.trim().lowercase()
        val match = actualFingerprints.firstOrNull { it.equals(cleanExpected, ignoreCase = true) }

        return if (match != null) {
            SignatureVerificationResult.Trusted(match)
        } else {
            SignatureVerificationResult.Mismatch(
                actualFingerprint = actualFingerprints.first(),
                expectedFingerprint = cleanExpected,
            )
        }
    }
}
