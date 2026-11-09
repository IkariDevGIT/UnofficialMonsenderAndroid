package io.github.unofficialmonsender.data.monloader

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException

class OutcomesTest {
    private val health = ApiResponse(200, """{"app":"monloader","status":"ok","version":"1.2.0","gallerydl_version":"1.30.0","api":2}""")

    @Test
    fun connectionStates() {
        assertEquals(ConnectionState.Unreachable, Outcomes.connection(ApiResponse(0, "", IOException()), null, false))
        assertEquals(ConnectionState.NotPaired, Outcomes.connection(health, ApiResponse(503, """{"error":"x","code":"api_disabled"}"""), false))
        assertEquals(ConnectionState.NotPaired, Outcomes.connection(health, ApiResponse(401, "{}"), false))
        assertEquals(ConnectionState.TokenRejected, Outcomes.connection(health, ApiResponse(401, "{}"), true))
        assertEquals(ConnectionState.SetupRequired, Outcomes.connection(health, ApiResponse(409, """{"code":"setup_required"}"""), true))
        assertEquals(ConnectionState.Online("1.2.0", "1.30.0"), Outcomes.connection(health, ApiResponse(200, """{"jobs":[]}"""), true))
        assertEquals(ConnectionState.NotMonLoader, Outcomes.connection(ApiResponse(200, """{"app":"monbooru","status":"ok"}"""), null, true))
    }

    @Test
    fun sendResults() {
        assertEquals(SendResult.Queued(42), Outcomes.send(ApiResponse(202, """{"job_id":42}"""), true))
        assertEquals(SendResult.Failed(SendFailure.UNREACHABLE), Outcomes.send(ApiResponse(0, "", IOException()), true))
        assertEquals(SendResult.Failed(SendFailure.TOKEN_REJECTED), Outcomes.send(ApiResponse(401, "{}"), true))
        assertEquals(
            SendFailure.MONBOORU_PAUSED,
            (Outcomes.send(ApiResponse(409, """{"error":"paused","code":"monbooru_paused"}"""), true) as SendResult.Failed).reason,
        )
    }
}
