package com.megasmsrouter.network

import com.megasmsrouter.domain.model.ApiDestination
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException

/** Executes requests off the main thread. TLS validation is the OkHttp/Android default (never disabled). */
class ApiClient(private val base: OkHttpClient = OkHttpClient()) {

    suspend fun send(request: Request, dest: ApiDestination): NetworkResult =
        withContext(Dispatchers.IO) { executeBlocking(request, dest) }

    fun executeBlocking(request: Request, dest: ApiDestination): NetworkResult {
        val start = System.nanoTime()
        fun elapsed(): Long = (System.nanoTime() - start) / 1_000_000

        if (request.url.scheme == "http" && !dest.allowHttp) {
            return NetworkResult.Failure(FailureKind.BLOCKED, "HttpNotAllowed", 0)
        }
        val connect = dest.connectTimeoutSec.coerceIn(1, 120).toLong()
        val read = dest.readTimeoutSec.coerceIn(1, 120).toLong()
        val client = base.newBuilder()
            .connectTimeout(connect, TimeUnit.SECONDS)
            .readTimeout(read, TimeUnit.SECONDS)
            .writeTimeout(read, TimeUnit.SECONDS)
            .callTimeout(connect + read * 2, TimeUnit.SECONDS)
            .followSslRedirects(dest.allowHttp) // never silently downgrade https -> http
            .build()

        // Error details contain only the exception class name: messages may include URLs or secrets.
        return try {
            client.newCall(request).execute().use { resp -> classify(resp, dest, elapsed()) }
        } catch (e: InterruptedIOException) {
            NetworkResult.Failure(FailureKind.TIMEOUT, e.javaClass.simpleName, elapsed())
        } catch (e: UnknownHostException) {
            NetworkResult.Failure(FailureKind.OFFLINE, e.javaClass.simpleName, elapsed())
        } catch (e: ConnectException) {
            NetworkResult.Failure(FailureKind.OFFLINE, e.javaClass.simpleName, elapsed())
        } catch (e: SSLException) {
            NetworkResult.Failure(FailureKind.TLS, e.javaClass.simpleName, elapsed())
        } catch (e: IOException) {
            NetworkResult.Failure(FailureKind.IO, e.javaClass.simpleName, elapsed())
        }
    }

    private fun classify(resp: Response, dest: ApiDestination, ms: Long): NetworkResult {
        val code = resp.code
        if (!resp.isSuccessful) return NetworkResult.HttpError(code, ms)
        if (dest.successContains.isNotBlank()) {
            val snippet = try {
                resp.peekBody(8192).string() // bounded read
            } catch (e: IOException) {
                ""
            }
            if (!snippet.contains(dest.successContains)) return NetworkResult.HttpError(code, ms, bodyMismatch = true)
        }
        return NetworkResult.Success(code, ms)
    }
}
