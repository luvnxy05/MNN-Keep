package com.alibaba.mnnllm.api.openai.manager

import android.content.Context
import com.alibaba.mnnllm.android.MnnLlmApplication

/**
 * Current model manager
 * Used to store and access the currently active model ID in the API service
 */
object CurrentModelManager {
    private const val PREFS_NAME = "current_model_prefs"
    private const val KEY_MODEL_ID = "model_id"
    private var currentModelId: String? = null
    private var loaded = false

    private fun prefs(): android.content.SharedPreferences =
        MnnLlmApplication.getAppContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    /**
     * Set current model ID
     */
    fun setCurrentModelId(modelId: String?) {
        currentModelId = modelId
        if (modelId == null) {
            prefs().edit().remove(KEY_MODEL_ID).apply()
        } else {
            prefs().edit().putString(KEY_MODEL_ID, modelId).apply()
        }
    }
    
    /**
     * Get current model ID
     */
    fun getCurrentModelId(): String? {
        if (!loaded) {
            currentModelId = prefs().getString(KEY_MODEL_ID, null)
            loaded = true
        }
        return currentModelId
    }
    
    /**
     * Clear current model ID
     */
    fun clearCurrentModelId() = setCurrentModelId(null)
}
