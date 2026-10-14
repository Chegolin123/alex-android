package com.alex.app.data.update

import com.alex.app.BuildConfig

/** Параметры самообновления: репозиторий и имя ассета с APK в релизах. */
object UpdateConfig {
    val REPO: String = BuildConfig.GITHUB_REPO
    val APK_ASSET_NAME: String = BuildConfig.APK_ASSET
    val LATEST_URL: String = "https://api.github.com/repos/$REPO/releases/latest"
}
