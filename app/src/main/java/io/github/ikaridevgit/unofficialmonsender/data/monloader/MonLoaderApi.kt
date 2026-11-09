package io.github.ikaridevgit.unofficialmonsender.data.monloader

import io.github.ikaridevgit.unofficialmonsender.link.Links
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

class ApiResponse(
    val status: Int,
    val body: String,
    val networkError: IOException? = null,
) {
    val isSuccessful: Boolean get() = status in 200..299
    val isNetworkError: Boolean get() = networkError != null

    fun <T> decode(deserializer: kotlinx.serialization.DeserializationStrategy<T>): T? =
        runCatching { MonLoaderApi.json.decodeFromString(deserializer, body) }.getOrNull()

    val error: ApiError? get() = if (isSuccessful) null else decode(ApiError.serializer())
}

/** HTTP client for MonLoader's REST API, mirroring the calls MonSender makes. */
class MonLoaderApi(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .build(),
) {
    suspend fun health(base: String) = get(base, "/health", token = null)

    suspend fun listQueue(base: String, token: String, limit: Int = 1) =
        get(base, "/api/v1/queue?limit=$limit", token)

    suspend fun getJob(base: String, token: String, id: Long) = get(base, "/api/v1/queue/$id", token)

    suspend fun listSites(base: String, token: String) = get(base, "/api/v1/sites", token)

    suspend fun enqueue(base: String, token: String, url: String) =
        post(base, "/api/v1/queue", token, json.encodeToString(EnqueueRequest.serializer(), EnqueueRequest(url)))

    suspend fun pairRequest(base: String, app: String) =
        post(base, "/api/v1/pair/request", token = null, body = json.encodeToString(
            PairOffer.serializer(),
            PairOffer(app = app, requestedScopes = listOf("read", "write")),
        ))

    suspend fun pairStatus(base: String, requestId: String) =
        get(base, "/api/v1/pair/status?id=" + java.net.URLEncoder.encode(requestId, "UTF-8"), token = null)

    suspend fun pairRemove(base: String, token: String) = post(base, "/api/v1/pair/remove", token, body = null)

    private val testServer = TestServer()

    private suspend fun get(base: String, path: String, token: String?): ApiResponse {
        if (base == Links.TEST_SERVER) return testServer.handle("GET", path, body = null)
        return execute(request(base, path, token).get().build())
    }

    private suspend fun post(base: String, path: String, token: String?, body: String?): ApiResponse {
        if (base == Links.TEST_SERVER) return testServer.handle("POST", path, body)
        val requestBody: RequestBody = (body ?: "").toRequestBody(if (body != null) JSON_TYPE else null)
        return execute(request(base, path, token).post(requestBody).build())
    }

    private fun request(base: String, path: String, token: String?): Request.Builder {
        val builder = Request.Builder().url(base.trimEnd('/') + path)
        if (!token.isNullOrEmpty()) builder.header("Authorization", "Bearer $token")
        return builder
    }

    private suspend fun execute(request: Request): ApiResponse = suspendCancellableCoroutine { cont ->
        val call = try {
            client.newCall(request)
        } catch (e: IllegalArgumentException) {
            cont.resume(ApiResponse(0, "", IOException(e)))
            return@suspendCancellableCoroutine
        }
        cont.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (cont.isActive) cont.resume(ApiResponse(0, "", e))
            }

            override fun onResponse(call: Call, response: Response) {
                val result = response.use {
                    try {
                        ApiResponse(it.code, it.body.string())
                    } catch (e: IOException) {
                        ApiResponse(0, "", e)
                    }
                }
                if (cont.isActive) cont.resume(result)
            }
        })
    }

    companion object {
        val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
        private val JSON_TYPE = "application/json".toMediaType()
    }
}
