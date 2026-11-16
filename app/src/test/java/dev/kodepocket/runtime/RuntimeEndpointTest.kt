package dev.kodepocket.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeEndpointTest {

    @Test
    fun readsThePortFromTheLoopbackUrl() {
        val endpoint = RuntimeEndpoint("http://127.0.0.1:4096", "opencode", "secret")
        assertEquals(4096, endpoint.port)
    }

    @Test
    fun buildsABasicAuthorizationHeader() {
        val endpoint = RuntimeEndpoint("http://127.0.0.1:8080", "opencode", "s3cret")
        val header = endpoint.authorization()
        assertTrue(header.startsWith("Basic "))
        val decoded = String(
            java.util.Base64.getDecoder().decode(header.removePrefix("Basic ")),
            Charsets.UTF_8,
        )
        assertEquals("opencode:s3cret", decoded)
    }
}
