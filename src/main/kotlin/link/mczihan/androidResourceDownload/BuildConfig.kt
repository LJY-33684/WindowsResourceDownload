package link.mczihan.androidResourceDownload

/**
 * Desktop equivalent of Android BuildConfig.
 * API base URL is the real backend extracted from the release APK.
 */
object BuildConfig {
    const val API_BASE_URL: String = "https://ardapi.mczihan.link/"
    const val DEMO_MODE: Boolean = false
    const val VERSION_NAME: String = "2.3.1"
    const val DEBUG: Boolean = true
}
