package io.github.ikaridevgit.unofficialmonsender.data.monloader

import android.os.SystemClock
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.random.Random

/**
 * A pretend MonLoader that answers in-process for the "test" server address, speaking the same
 * JSON as the real API so every screen and flow behaves as it would against a real instance.
 * Jobs move from queued to running to a finished state over a few seconds.
 */
internal class TestServer {
    private class FakeJob(val createdAt: Long, val outcome: Outcome, val created: Int, val duplicate: Int)

    private enum class Outcome { SUCCEEDED, PARTIAL, FAILED }

    private val jobs = ConcurrentHashMap<Long, FakeJob>()
    private val nextId = AtomicLong(1000)
    @Volatile private var pairRequestedAt = 0L

    suspend fun handle(method: String, path: String, body: String?): ApiResponse {
        delay(Random.nextLong(120, 380))
        val route = path.substringBefore('?')
        return when {
            route == "/health" -> ok(HEALTH)
            method == "GET" && route == "/api/v1/queue" -> ok("""{"page":1,"limit":1,"total":${jobs.size},"paused":false,"jobs":[]}""")
            method == "GET" && route == "/api/v1/sites" -> ok(SITES)
            method == "POST" && route == "/api/v1/queue" -> enqueue(body)
            method == "GET" && route.startsWith("/api/v1/queue/") -> job(route.removePrefix("/api/v1/queue/").toLongOrNull())
            method == "POST" && route == "/api/v1/pair/request" -> {
                pairRequestedAt = SystemClock.elapsedRealtime()
                ApiResponse(202, """{"request_id":"test","status":"pending"}""")
            }
            method == "GET" && route == "/api/v1/pair/status" -> {
                val approved = SystemClock.elapsedRealtime() - pairRequestedAt > PAIR_APPROVAL_MS
                ok(if (approved) """{"status":"approved","token":"$TOKEN"}""" else """{"status":"pending"}""")
            }
            method == "POST" && route == "/api/v1/pair/remove" -> ok("{}")
            else -> ApiResponse(404, """{"error":"not found","code":"not_found"}""")
        }
    }

    private fun enqueue(body: String?): ApiResponse {
        val url = body?.let { runCatching { Json.parseToJsonElement(it).jsonObject["url"]?.jsonPrimitive?.content }.getOrNull() }
        if (url.isNullOrBlank() || !(url.startsWith("http://") || url.startsWith("https://"))) {
            return ApiResponse(400, """{"error":"url must be http(s)","code":"invalid_request"}""")
        }
        val roll = Random.nextInt(100)
        val outcome = when {
            roll < 80 -> Outcome.SUCCEEDED
            roll < 90 -> Outcome.PARTIAL
            else -> Outcome.FAILED
        }
        val id = nextId.incrementAndGet()
        jobs[id] = FakeJob(SystemClock.elapsedRealtime(), outcome, Random.nextInt(1, 13), Random.nextInt(0, 4))
        return ApiResponse(202, """{"job_id":$id}""")
    }

    private fun job(id: Long?): ApiResponse {
        val job = id?.let { jobs[it] } ?: return ApiResponse(404, """{"error":"job not found","code":"not_found"}""")
        val age = SystemClock.elapsedRealtime() - job.createdAt
        val json = when {
            age < QUEUED_MS -> """{"id":$id,"status":"queued"}"""
            age < QUEUED_MS + RUNNING_MS -> """{"id":$id,"status":"running"}"""
            else -> when (job.outcome) {
                Outcome.SUCCEEDED -> """{"id":$id,"status":"succeeded","summary":{"created":${job.created},"duplicate":${job.duplicate}}}"""
                Outcome.PARTIAL -> """{"id":$id,"status":"partial","summary":{"created":${job.created},"failed":2},"error":"test: 2 posts could not be downloaded"}"""
                Outcome.FAILED -> """{"id":$id,"status":"failed","error":"test: simulated download failure"}"""
            }
        }
        return ok(json)
    }

    private fun ok(json: String) = ApiResponse(200, json)

    companion object {
        const val TOKEN = "test-token"
        private const val PAIR_APPROVAL_MS = 1_500L
        private const val QUEUED_MS = 2_000L
        private const val RUNNING_MS = 4_000L
        private const val HEALTH = """{"app":"monloader","status":"ok","version":"test","gallerydl_version":"test","api":1}"""
        private val SITES = """
            {"total":8,"sites":[
              {"category":"danbooru","name":"Danbooru","example":"https://danbooru.donmai.us/posts/1","hosts":["donmai.us"]},
              {"category":"gelbooru","name":"Gelbooru","example":"https://gelbooru.com/index.php?page=post&s=view&id=1","hosts":[]},
              {"category":"safebooru","name":"Safebooru","example":"https://safebooru.org/index.php?page=post&s=view&id=1","hosts":[]},
              {"category":"yandere","name":"yande.re","example":"https://yande.re/post/show/1","hosts":[]},
              {"category":"konachan","name":"Konachan","example":"https://konachan.com/post/show/1","hosts":["konachan.net"]},
              {"category":"pixiv","name":"pixiv","example":"https://www.pixiv.net/artworks/1","hosts":["pximg.net"]},
              {"category":"twitter","name":"X","example":"https://x.com/user/status/1","hosts":["twitter.com"]},
              {"category":"bluesky","name":"Bluesky","example":"https://bsky.app/profile/user/post/1","hosts":[]}
            ]}
        """.trimIndent()
    }
}
