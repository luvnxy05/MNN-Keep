package com.alibaba.mnnllm.android.console

import android.content.Context
import androidx.preference.PreferenceManager
import com.alibaba.mnnllm.api.openai.manager.CurrentModelManager
import com.alibaba.mnnllm.api.openai.service.ApiServerConfig
import org.json.JSONObject

/**
 * Backup / restore of server config: API settings (port, ip, api key, cors,
 * auth) + current model + service toggle. Lets users migrate to a new device
 * in one file instead of re-configuring everything by hand.
 */
object ConfigBackup {

    private const val FORMAT_VERSION = 1

    fun toJson(context: Context): String {
        val useHttps = context.getSharedPreferences("api_settings", Context.MODE_PRIVATE)
            .getBoolean("use_https_url", false)
        val api = JSONObject()
            .put("port", ApiServerConfig.getPort(context))
            .put("ip_address", ApiServerConfig.getIpAddress(context))
            .put("cors_enabled", ApiServerConfig.isCorsEnabled(context))
            .put("cors_origins", ApiServerConfig.getCorsOrigins(context))
            .put("auth_enabled", ApiServerConfig.isAuthEnabled(context))
            .put("api_key", ApiServerConfig.getApiKey(context))
            .put("use_https_url", useHttps)
        val serviceEnabled = PreferenceManager.getDefaultSharedPreferences(context)
            .getBoolean("enable_api_service", false)
        return JSONObject()
            .put("app", "MNN Keep")
            .put("version", FORMAT_VERSION)
            .put("api", api)
            .put("model_id", CurrentModelManager.getCurrentModelId() ?: "")
            .put("service_enabled", serviceEnabled)
            .toString(2)
    }

    fun fromJson(context: Context, json: String): Boolean {
        return try {
            val root = JSONObject(json)
            if (root.optString("app") != "MNN Keep") return false
            val api = root.getJSONObject("api")
            ApiServerConfig.saveConfig(
                context,
                port = api.optInt("port", 8080),
                ipAddress = api.optString("ip_address", "0.0.0.0"),
                corsEnabled = api.optBoolean("cors_enabled", false),
                corsOrigins = api.optString("cors_origins", ""),
                authEnabled = api.optBoolean("auth_enabled", true),
                apiKey = api.optString("api_key", ""),
                useHttpsUrl = api.optBoolean("use_https_url", false)
            )
            CurrentModelManager.setCurrentModelId(root.optString("model_id", "").ifEmpty { null })
            PreferenceManager.getDefaultSharedPreferences(context).edit()
                .putBoolean("enable_api_service", root.optBoolean("service_enabled", false))
                .apply()
            true
        } catch (e: Exception) {
            false
        }
    }
}
