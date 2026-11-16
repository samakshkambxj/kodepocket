package dev.kodepocket.runtime

sealed interface RuntimeState {
    data object Idle : RuntimeState
    data class Starting(val port: Int) : RuntimeState
    data class Running(
        val port: Int,
        val version: String,
        val persistentPty: Boolean,
    ) : RuntimeState

    data object Stopping : RuntimeState
    data class Failed(val message: String) : RuntimeState
}

/**
 * Loopback endpoint and credentials. Kept out of [RuntimeState] so the
 * password is never rendered or logged by the UI.
 */
data class RuntimeEndpoint(
    val url: String,
    val username: String,
    val password: String,
) {
    val port: Int get() = url.substringAfterLast(':').toInt()

    /**
     * `java.util.Base64` rather than `android.util.Base64` so the header can be
     * exercised by plain JVM unit tests.
     */
    fun authorization(): String {
        val credentials = "$username:$password".toByteArray(Charsets.UTF_8)
        return "Basic " + java.util.Base64.getEncoder().encodeToString(credentials)
    }

    override fun toString(): String = "RuntimeEndpoint(url=$url, username=$username, password=***)"
}
