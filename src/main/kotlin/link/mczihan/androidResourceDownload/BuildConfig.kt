package link.mczihan.androidResourceDownload

/**
 * Desktop equivalent of Android BuildConfig.
 * API base URL is the real backend extracted from the release APK.
 */
object BuildConfig {
    const val API_BASE_URL: String = "https://ardapi.mczihan.link/"
    const val DEMO_MODE: Boolean = false
    const val VERSION_NAME: String = "2.3.9"
    const val DEBUG: Boolean = true

    /** QQ 互联 AppID（与安卓端一致，公开标识，非 secret）。 */
    const val QQ_APP_ID: String = "1905483457"

    /**
     * QQ 互联网页授权回调地址（client-side，response_type=token）。
     * 必须与 QQ 互联后台注册的域名匹配；WebView 内捕获 #access_token，无需后端接收。
     * 若授权页报 redirect 错误，需与 QQ 互联后台确认注册的回调域名后修改此项。
     */
    const val QQ_REDIRECT_URI: String = "https://ardapi.mczihan.link/api/v1/auth/qq/callback"
}
