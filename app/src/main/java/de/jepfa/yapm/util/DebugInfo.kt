package de.jepfa.yapm.util

import android.content.Context
import android.os.Build
import de.jepfa.yapm.BuildConfig
import de.jepfa.yapm.database.YapmDatabase
import de.jepfa.yapm.service.nfc.NfcService

object DebugInfo {

    private var debug = BuildConfig.DEBUG

    val isDebug: Boolean
        get() = debug

    @Synchronized
    fun toggleDebug() {
        debug = !debug
    }

    fun getVersionName(context: Context): String {
        val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        return pInfo.versionName
    }

    fun getVersionCode(context: Context): Int {
        val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        return pInfo.versionCode
    }

    fun getDebugInfo(context: Context): String {
        val sb = StringBuilder()
        sb.append("\n************ APP INFORMATION ***********\n")
        sb.addLabelLine("Version", getVersionName(context))
        sb.addLabelLine("VersionCode", getVersionCode(context))
        sb.addLabelLine("Database Version", YapmDatabase.getVersion())
        sb.addLabelLine("BuildTimestamp", Constants.SDF_DT_MEDIUM.format(BuildConfig.BUILD_TIME))
        sb.addLabelLine("BuildType", BuildConfig.BUILD_TYPE)

        sb.append("\n************ DEVICE INFORMATION ***********\n")
        sb.addLabelLine("Brand", Build.BRAND)
        sb.addLabelLine("Device", Build.DEVICE)
        sb.addLabelLine("Model", Build.MODEL)
        sb.addLabelLine("Id", Build.ID)
        sb.addLabelLine("Product", Build.PRODUCT)
        sb.addLabelLine("NFC available", NfcService.isNfcAvailable(context))
        sb.addLabelLine("NFC enabled", NfcService.isNfcEnabled(context))

        sb.append("\n************ PERMISSIONS ************\n")
        sb.addLabelLine("Read Storage granted", PermissionChecker.hasReadStoragePermissions(context))
        sb.addLabelLine("Read/write storage granted", PermissionChecker.hasRWStoragePermissions(context))
        sb.addLabelLine("Overlay dialog granted", PermissionChecker.hasOverlayPermission(context))
        sb.addLabelLine("Camera granted", PermissionChecker.hasCameraPermission(context))

        sb.append("\n************ FIRMWARE ************\n")
        sb.addLabelLine("SDK", Build.VERSION.SDK)
        sb.addLabelLine("Release", Build.VERSION.RELEASE)
        sb.addLabelLine("Incremental", Build.VERSION.INCREMENTAL)

        return sb.toString()
    }
}