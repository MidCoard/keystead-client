package top.focess.keystead.client

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

enum class ServerAvailability {
    CHECKING,
    ONLINE,
    OFFLINE,
    ;

    val isOnline: Boolean
        get() = this == ONLINE
}

object ServerAvailabilityTransitions {
    fun afterServerAction(
        current: ServerAvailability,
        error: Throwable?,
    ): ServerAvailability =
        when (error) {
            null -> ServerAvailability.ONLINE
            is java.io.IOException -> ServerAvailability.OFFLINE
            is KeysteadServerException -> ServerAvailability.ONLINE
            else -> current
        }
}

class ServerAvailabilityChecker(
    private val http: HttpClient =
        HttpClient.newBuilder()
            .connectTimeout(DEFAULT_TIMEOUT)
            .build(),
    private val requestTimeout: Duration = DEFAULT_TIMEOUT,
) {
    fun check(baseUrl: String): ServerAvailability {
        val normalized = baseUrl.trim().trimEnd('/')
        if (normalized.isEmpty()) return ServerAvailability.OFFLINE

        return try {
            val request =
                HttpRequest.newBuilder(URI.create("$normalized/actuator/health"))
                    .timeout(requestTimeout)
                    .GET()
                    .build()
            val response = http.send(request, HttpResponse.BodyHandlers.discarding())
            if (response.statusCode() in 200..299) {
                ServerAvailability.ONLINE
            } else {
                ServerAvailability.OFFLINE
            }
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            ServerAvailability.OFFLINE
        } catch (_: Exception) {
            ServerAvailability.OFFLINE
        }
    }

    private companion object {
        val DEFAULT_TIMEOUT: Duration = Duration.ofSeconds(3)
    }
}
