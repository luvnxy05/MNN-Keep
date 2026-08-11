package com.alibaba.mnnllm.api.openai.network.application

import android.content.Context
import android.content.SharedPreferences

/**
 * Request statistics for the API server: request count, success rate, and
 * token usage. Persisted in SharedPreferences so stats survive process
 * restarts / crashes (a headless server must not lose its counters).
 */
object RequestStats {
    private const val PREFS = "request_stats"
    private const val K_TOTAL = "total"
    private const val K_OK = "ok"
    private const val K_PROMPT = "prompt_tokens"
    private const val K_COMPLETION = "completion_tokens"

    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        if (!::prefs.isInitialized) {
            prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        }
    }

    @Synchronized
    fun recordRequest(succeeded: Boolean) {
        prefs.edit()
            .putInt(K_TOTAL, total() + 1)
            .putInt(K_OK, ok() + (if (succeeded) 1 else 0))
            .apply()
    }

    @Synchronized
    fun recordTokens(prompt: Int, completion: Int) {
        prefs.edit()
            .putLong(K_PROMPT, promptTokens() + prompt)
            .putLong(K_COMPLETION, completionTokens() + completion)
            .apply()
    }

    fun total() = prefs.getInt(K_TOTAL, 0)
    fun ok() = prefs.getInt(K_OK, 0)
    fun promptTokens() = prefs.getLong(K_PROMPT, 0L)
    fun completionTokens() = prefs.getLong(K_COMPLETION, 0L)

    @Synchronized
    fun snapshot(): String {
        val t = total()
        val failRate = if (t == 0) 0 else (t - ok()) * 100 / t
        return "req=$t ok=${ok()} fail=${t - ok()}($failRate%) tok=${promptTokens() + completionTokens()}(p${promptTokens()}/c${completionTokens()})"
    }
}
