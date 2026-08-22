package link.mczihan.androidResourceDownload.feature.auth

data class PendingOAuth(
    val appState: String,
    val verifier: String,
    val createdAtMillis: Long,
)

/**
 * Desktop in-memory PendingOAuthStore. No encryption needed for local memory.
 */
class DesktopPendingOAuthStore {
    @Volatile private var pending: PendingOAuth? = null

    fun save(transaction: PendingOAuth) {
        pending = transaction
    }

    fun read(): PendingOAuth? = pending

    fun clear() {
        pending = null
    }
}
