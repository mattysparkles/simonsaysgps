package com.simonsaysgps.domain.model

enum class FetchSource {
    NETWORK,
    CACHE
}

enum class NetworkFailureType {
    NO_NETWORK,
    TIMEOUT,
    SERVER,
    UNKNOWN
}

data class NetworkFailure(
    val type: NetworkFailureType,
    val detail: String? = null
)

sealed interface RepositoryResult<out T> {
    data class Success<T>(
        val value: T,
        val source: FetchSource,
        val fallbackFailure: NetworkFailure? = null
    ) : RepositoryResult<T>

    data class Failure(
        val failure: NetworkFailure
    ) : RepositoryResult<Nothing>
}
