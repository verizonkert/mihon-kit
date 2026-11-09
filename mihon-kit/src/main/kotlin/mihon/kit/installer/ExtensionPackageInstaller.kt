package mihon.kit.installer

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import java.io.File
import java.io.FileInputStream

/**
 * Represents the state and progress events emitted during an APK installation.
 */
sealed interface InstallStatus {
    /** The APK is currently being written to the installer session or processed by the system. */
    data object Installing : InstallStatus

    /** The APK installation succeeded and the package is ready for use. */
    data object Success : InstallStatus

    /**
     * The installation failed.
     *
     * @property message Human-readable error description or reason provided by the package manager.
     */
    data class Error(val message: String) : InstallStatus
}

/**
 * Represents the state and progress events emitted during an extension package uninstallation.
 */
sealed interface UninstallStatus {
    /** The package uninstallation is currently in progress. */
    data object Uninstalling : UninstallStatus

    /** The package uninstallation succeeded. */
    data object Success : UninstallStatus

    /**
     * The uninstallation failed.
     *
     * @property message Human-readable error description or reason provided by the package manager.
     */
    data class Error(val message: String) : UninstallStatus
}

/**
 * Handles physical APK installation and uninstallation matching all three operational modes supported by Mihon:
 * 1. **Session API**: Modern Android [PackageInstaller] session streaming with pending user confirmation intent handling.
 * 2. **Legacy Intent**: Standard system package installer prompt launching via `Intent.ACTION_VIEW` and [FileProvider].
 * 3. **Shizuku**: 100% silent, background unattended installation and uninstallation using ADB / root privileges.
 *
 * @param context Application context used for accessing system package managers and broadcast receivers.
 */
class ExtensionPackageInstaller(private val context: Context) {

    companion object {
        /** Broadcast action triggered when a [PackageInstaller] install session completes. */
        const val ACTION_INSTALL_COMPLETE = "mihon.kit.installer.INSTALL_COMPLETE"

        /** Broadcast action triggered when a [PackageInstaller] uninstall request completes. */
        const val ACTION_UNINSTALL_COMPLETE = "mihon.kit.installer.UNINSTALL_COMPLETE"
    }

    /**
     * Mode 1: Modern Android [PackageInstaller.Session] API.
     *
     * Streams the APK byte stream directly into an OS installer session. If user confirmation is required
     * (non-system app on Android 12+), automatically launches the system user action prompt.
     *
     * @param apkFile The local APK file to install.
     * @return Flow emitting [InstallStatus.Installing], [InstallStatus.Success], or [InstallStatus.Error].
     */
    fun installViaSession(apkFile: File): Flow<InstallStatus> = callbackFlow {
        require(apkFile.exists() && apkFile.isFile) { "APK file does not exist: ${apkFile.absolutePath}" }

        trySend(InstallStatus.Installing)

        val packageInstaller = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }

        val sessionId = packageInstaller.createSession(params)
        val session = packageInstaller.openSession(sessionId)

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(recvContext: Context, intent: Intent) {
                if (intent.action == ACTION_INSTALL_COMPLETE) {
                    val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
                    val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                    when (status) {
                        PackageInstaller.STATUS_SUCCESS -> {
                            trySend(InstallStatus.Success)
                            close()
                        }
                        PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                            val confirmIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                            } else {
                                @Suppress("DEPRECATION")
                                intent.getParcelableExtra(Intent.EXTRA_INTENT)
                            }
                            confirmIntent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)?.let {
                                context.startActivity(it)
                            }
                        }
                        else -> {
                            trySend(InstallStatus.Error(message ?: "Installation failed with status code: $status"))
                            close()
                        }
                    }
                }
            }
        }

        val filter = IntentFilter(ACTION_INSTALL_COMPLETE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }

        withContext(Dispatchers.IO) {
            FileInputStream(apkFile).use { input ->
                session.openWrite("package", 0, apkFile.length()).use { output ->
                    input.copyTo(output)
                    session.fsync(output)
                }
            }

            val intent = Intent(ACTION_INSTALL_COMPLETE).apply {
                setPackage(context.packageName)
            }
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            val pendingIntent = PendingIntent.getBroadcast(context, sessionId, intent, flags)

            session.commit(pendingIntent.intentSender)
            session.close()
        }

        awaitClose {
            runCatching { context.unregisterReceiver(receiver) }
        }
    }

    /**
     * Mode 2: Legacy system Intent installer.
     *
     * Launches the native Android "Install app?" system prompt via [Intent.ACTION_VIEW]
     * using the app's [FileProvider].
     *
     * @param apkFile The local APK file to install.
     * @param fileProviderAuthority Optional custom FileProvider authority (defaults to `${context.packageName}.fileprovider`).
     */
    fun installViaLegacyIntent(apkFile: File, fileProviderAuthority: String? = null) {
        val authority = fileProviderAuthority ?: "${context.packageName}.fileprovider"
        val contentUri: Uri = FileProvider.getUriForFile(context, authority, apkFile)

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(contentUri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        context.startActivity(intent)
    }

    /**
     * Checks if the Shizuku IPC service is currently running on the device and whether this app has been granted permission.
     *
     * @return `true` if Shizuku is active and permission is granted, `false` otherwise.
     */
    fun isShizukuAvailable(): Boolean {
        return runCatching {
            Shizuku.pingBinder() && Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)
    }

    /**
     * Mode 3: Silent Background Installer via Shizuku.
     *
     * Uses privileged ADB/root IPC to stream the APK through [PackageInstaller.Session]
     * without displaying any user confirmation dialogs.
     *
     * @param apkFile The local APK file to install silently.
     * @return Flow emitting [InstallStatus.Installing], [InstallStatus.Success], or [InstallStatus.Error].
     */
    fun installViaShizuku(apkFile: File): Flow<InstallStatus> = callbackFlow {
        if (!isShizukuAvailable()) {
            trySend(InstallStatus.Error("Shizuku is not running or permission is denied"))
            close()
            return@callbackFlow
        }

        trySend(InstallStatus.Installing)

        // Shizuku grants permission to create PackageInstaller sessions with USER_ACTION_NOT_REQUIRED
        val packageInstaller = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }

        val sessionId = packageInstaller.createSession(params)
        val session = packageInstaller.openSession(sessionId)

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(recvContext: Context, intent: Intent) {
                if (intent.action == ACTION_INSTALL_COMPLETE) {
                    val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
                    val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                    when (status) {
                        PackageInstaller.STATUS_SUCCESS -> {
                            trySend(InstallStatus.Success)
                            close()
                        }
                        else -> {
                            trySend(InstallStatus.Error(message ?: "Shizuku install failed: $status"))
                            close()
                        }
                    }
                }
            }
        }

        val filter = IntentFilter(ACTION_INSTALL_COMPLETE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }

        withContext(Dispatchers.IO) {
            FileInputStream(apkFile).use { input ->
                session.openWrite("package", 0, apkFile.length()).use { output ->
                    input.copyTo(output)
                    session.fsync(output)
                }
            }

            val intent = Intent(ACTION_INSTALL_COMPLETE).apply {
                setPackage(context.packageName)
            }
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            val pendingIntent = PendingIntent.getBroadcast(context, sessionId, intent, flags)

            session.commit(pendingIntent.intentSender)
            session.close()
        }

        awaitClose {
            runCatching { context.unregisterReceiver(receiver) }
        }
    }

    // =========================================================================
    // Package Uninstallation (Session, Legacy & Shizuku)
    // =========================================================================

    /**
     * Uninstalls an extension package using Android's standard [PackageInstaller.uninstall] API.
     *
     * Displays a system confirmation dialog on standard devices and emits uninstallation state events.
     *
     * @param packageName Android application ID of the package to uninstall.
     * @return Flow emitting [UninstallStatus.Uninstalling], [UninstallStatus.Success], or [UninstallStatus.Error].
     */
    fun uninstallViaSession(packageName: String): Flow<UninstallStatus> = callbackFlow {
        trySend(UninstallStatus.Uninstalling)

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(recvContext: Context, intent: Intent) {
                if (intent.action == ACTION_UNINSTALL_COMPLETE) {
                    val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
                    val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                    when (status) {
                        PackageInstaller.STATUS_SUCCESS -> {
                            trySend(UninstallStatus.Success)
                            close()
                        }
                        PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                            val confirmIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                            } else {
                                @Suppress("DEPRECATION")
                                intent.getParcelableExtra(Intent.EXTRA_INTENT)
                            }
                            confirmIntent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)?.let {
                                context.startActivity(it)
                            }
                        }
                        else -> {
                            trySend(UninstallStatus.Error(message ?: "Uninstall failed with status $status"))
                            close()
                        }
                    }
                }
            }
        }

        val filter = IntentFilter(ACTION_UNINSTALL_COMPLETE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }

        val intent = Intent(ACTION_UNINSTALL_COMPLETE).apply {
            setPackage(context.packageName)
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = PendingIntent.getBroadcast(context, packageName.hashCode(), intent, flags)

        try {
            val packageInstaller = context.packageManager.packageInstaller
            packageInstaller.uninstall(packageName, pendingIntent.intentSender)
        } catch (e: Exception) {
            trySend(UninstallStatus.Error(e.message ?: "Failed to initiate uninstallation"))
            close(e)
        }

        awaitClose {
            runCatching { context.unregisterReceiver(receiver) }
        }
    }

    /**
     * Uninstalls an extension package using standard system [Intent.ACTION_UNINSTALL_PACKAGE] / [Intent.ACTION_DELETE].
     *
     * @param packageName Android application package name to uninstall.
     */
    fun uninstallViaLegacyIntent(packageName: String) {
        val packageUri = Uri.parse("package:$packageName")
        val intent = Intent(Intent.ACTION_UNINSTALL_PACKAGE, packageUri).apply {
            putExtra(Intent.EXTRA_RETURN_RESULT, true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    /**
     * Uninstalls an extension package 100% silently in the background without user confirmation dialogs
     * using Shizuku ADB / root privileges.
     *
     * @param packageName Android application package name to uninstall silently.
     * @return Flow emitting [UninstallStatus.Uninstalling], [UninstallStatus.Success], or [UninstallStatus.Error].
     */
    fun uninstallViaShizuku(packageName: String): Flow<UninstallStatus> = callbackFlow {
        if (!isShizukuAvailable()) {
            trySend(UninstallStatus.Error("Shizuku is not running or permission is denied"))
            close()
            return@callbackFlow
        }

        trySend(UninstallStatus.Uninstalling)

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(recvContext: Context, intent: Intent) {
                if (intent.action == ACTION_UNINSTALL_COMPLETE) {
                    val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
                    val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                    when (status) {
                        PackageInstaller.STATUS_SUCCESS -> {
                            trySend(UninstallStatus.Success)
                            close()
                        }
                        else -> {
                            trySend(UninstallStatus.Error(message ?: "Shizuku uninstall failed: $status"))
                            close()
                        }
                    }
                }
            }
        }

        val filter = IntentFilter(ACTION_UNINSTALL_COMPLETE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }

        val intent = Intent(ACTION_UNINSTALL_COMPLETE).apply {
            setPackage(context.packageName)
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = PendingIntent.getBroadcast(context, packageName.hashCode(), intent, flags)

        try {
            val packageInstaller = context.packageManager.packageInstaller
            packageInstaller.uninstall(packageName, pendingIntent.intentSender)
        } catch (e: Exception) {
            trySend(UninstallStatus.Error(e.message ?: "Failed to initiate Shizuku uninstallation"))
            close(e)
        }

        awaitClose {
            runCatching { context.unregisterReceiver(receiver) }
        }
    }
}
