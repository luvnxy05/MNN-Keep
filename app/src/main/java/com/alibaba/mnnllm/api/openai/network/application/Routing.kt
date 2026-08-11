package com.alibaba.mnnllm.api.openai.network.application

import com.alibaba.mnnllm.api.openai.network.routes.anthropicRoutes
import com.alibaba.mnnllm.api.openai.network.routes.chatRoutes
import com.alibaba.mnnllm.api.openai.network.routes.modelsRoutes
import com.alibaba.mnnllm.api.openai.network.routes.queueRoutes
import io.ktor.http.ContentType
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.auth.authenticate
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.server.request.uri
import io.ktor.server.plugins.origin
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.sse.SSE
import io.ktor.server.sse.sse
import io.ktor.sse.ServerSentEvent
import java.io.InputStream
import io.ktor.util.pipeline.intercept
import timber.log.Timber

fun Application.configureRouting() {
    install(SSE)

    routing {
        // Request log for the API console (LogCollector picks up
        // "RequestProcessing" tag): method path -> status duration [client-ip].
        intercept(ApplicationCallPipeline.Call) {
            val start = System.currentTimeMillis()
            try {
                proceed()
            } finally {
                val path = call.request.path()
                if (path.startsWith("/v1/")) {
                    RequestStats.recordRequest((call.response.status()?.value ?: 500) < 400)
                    Timber.tag("RequestProcessing").i(
                        "HTTP %s %s -> %s %dms [%s]",
                        call.request.httpMethod.value,
                        path,
                        call.response.status(),
                        System.currentTimeMillis() - start,
                        call.request.origin.remoteHost
                    )
                }
            }
        }
        get("/") {
            try {
                val htmlContent = loadHtmlFromAssets()
                call.respondText(htmlContent, contentType = ContentType.Text.Html)
            } catch (e: Exception) {
                call.respondText("Error loading test page: ${e.message}", contentType = ContentType.Text.Plain)
            }
        }

        sse("/hello") {
            send(ServerSentEvent("world"))
        }

        queueRoutes()
        modelsRoutes()

        // Anthropic-compatible endpoint (/v1/messages) with x-api-key or bearer auth.
        anthropicRoutes()

        authenticate("auth-bearer") {
            chatRoutes()
        }
    }

}

private fun loadHtmlFromAssets(): String {
    return try {
        val context = com.alibaba.mnnllm.android.MnnLlmApplication.getAppContext()
        val inputStream: InputStream = context.assets.open("test_page.html")
        inputStream.bufferedReader().use { reader ->
            reader.readText()
        }
    } catch (e: Exception) {
        throw Exception("Failed to load HTML from assets: ${e.message}")
    }
}
