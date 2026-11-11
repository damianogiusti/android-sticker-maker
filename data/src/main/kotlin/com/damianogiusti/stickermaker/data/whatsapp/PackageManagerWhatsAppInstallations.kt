package com.damianogiusti.stickermaker.data.whatsapp

import android.content.Context
import android.content.pm.PackageManager
import com.damianogiusti.stickermaker.domain.model.WhatsAppApp
import com.damianogiusti.stickermaker.domain.repository.WhatsAppInstallations

/** Needs `<queries>` for exactly the packages of [WhatsAppApp] in the app manifest. */
class PackageManagerWhatsAppInstallations(private val context: Context) : WhatsAppInstallations {

    override suspend fun installedApps(): List<WhatsAppApp> = WhatsAppApp.entries.filter(::isInstalled)

    @Suppress("DEPRECATION") // The flags overload of API 33 has no clean replacement for minSdk 24.
    private fun isInstalled(app: WhatsAppApp): Boolean = try {
        context.packageManager.getPackageInfo(app.packageName, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }
}
