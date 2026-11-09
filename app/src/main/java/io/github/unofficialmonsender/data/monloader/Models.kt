package io.github.unofficialmonsender.data.monloader

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Health(
    val app: String? = null,
    val status: String? = null,
    val version: String? = null,
    @SerialName("gallerydl_version") val galleryDlVersion: String? = null,
    val api: Int = 0,
)

@Serializable
data class ApiError(
    val error: String? = null,
    val code: String? = null,
)

@Serializable
data class Site(
    val category: String = "",
    val subcategory: String = "",
    val name: String? = null,
    val example: String = "",
    val hosts: List<String> = emptyList(),
)

@Serializable
data class SitesResponse(
    val total: Int = 0,
    val sites: List<Site> = emptyList(),
)

@Serializable
data class PairOffer(
    val app: String,
    @SerialName("requested_scopes") val requestedScopes: List<String>,
)

@Serializable
data class PairAccepted(
    @SerialName("request_id") val requestId: String = "",
    val status: String = "",
)

@Serializable
data class PairStatus(
    val status: String = "",
    val token: String? = null,
)

@Serializable
data class EnqueueRequest(val url: String)

@Serializable
data class JobAccepted(@SerialName("job_id") val jobId: Long = 0)

@Serializable
data class JobRef(val id: Long = 0)

@Serializable
data class JobSummary(
    val created: Int = 0,
    val duplicate: Int = 0,
    val skipped: Int = 0,
    val failed: Int = 0,
)

@Serializable
data class Job(
    val id: Long = 0,
    val status: String = "",
    val summary: JobSummary? = null,
    val error: String? = null,
)
