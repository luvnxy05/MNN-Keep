package com.alibaba.mnnllm.android.console

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.app.ActivityManager
import android.graphics.Color
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.alibaba.mnnllm.android.R
import com.alibaba.mnnllm.android.chat.ChatActivity
import com.alibaba.mnnllm.android.chat.model.ChatDataManager
import com.alibaba.mnnllm.android.databinding.ActivityServiceConsoleBinding
import com.alibaba.mnnllm.android.main.MainActivity
import com.alibaba.mls.api.download.ModelDownloadManager
import com.alibaba.mnnllm.api.openai.ui.ApiSettingsBottomSheetFragment
import com.alibaba.mnnllm.api.openai.ui.ApiConsoleBottomSheetFragment
import com.alibaba.mnnllm.api.openai.manager.ApiServiceManager
import com.alibaba.mnnllm.api.openai.manager.CurrentModelManager
import com.alibaba.mnnllm.api.openai.manager.ServerEventManager
import com.alibaba.mnnllm.api.openai.service.ApiServerConfig
import androidx.preference.PreferenceManager
import com.alibaba.mnnllm.android.utils.CrashUtil
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.net.NetworkInterface
import java.util.Date
import java.util.Collections

/**
 * Service console — the home screen for headless inference deployments.
 * Shows API service state, exposes a one-tap start/stop switch and quick
 * links to chat / model management / API settings.
 */
class ServiceConsoleActivity : AppCompatActivity() {

    private lateinit var binding: ActivityServiceConsoleBinding
    private val serviceSwitchListener =
        android.widget.CompoundButton.OnCheckedChangeListener { _, checked ->
            if (checked) startApiService() else stopApiService()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityServiceConsoleBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.switchService.setOnCheckedChangeListener(serviceSwitchListener)
        binding.buttonChat.setOnClickListener {
            // Let the user pick from their downloaded models (My Models tab)
            // instead of force-entering chat with the persisted service model.
            startActivity(Intent(this, MainActivity::class.java))
        }
        binding.buttonModels.setOnClickListener {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .putExtra(MainActivity.EXTRA_SELECT_TAB, MainActivity.TAB_MODEL_MARKET)
            )
        }
        binding.buttonApiSettings.setOnClickListener {
            // Original detailed API settings (port / IP / API key / CORS).
            ApiSettingsBottomSheetFragment().show(supportFragmentManager, "ApiSettingsBottomSheetFragment")
        }
        binding.buttonCrashLogs.setOnClickListener { showCrashLogs() }
        binding.buttonKeepAlive.setOnClickListener { KeepAliveGuide.show(this) }
        binding.buttonApiConsole.setOnClickListener {
            ApiConsoleBottomSheetFragment().show(supportFragmentManager, "ApiConsoleBottomSheetFragment")
        }
        binding.layoutModelRow.setOnClickListener { showModelPicker() }
        binding.buttonSwitchModel.setOnClickListener { showModelPicker() }
        binding.buttonCopyKey.setOnClickListener {
            copyToClipboard(ApiServerConfig.getApiKey(this))
            Toast.makeText(this, R.string.api_key_copied, Toast.LENGTH_SHORT).show()
        }

        observeServerState()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun observeServerState() {
        lifecycleScope.launch {
            ServerEventManager.getInstance().serverState.collect { refresh() }
        }
    }

    private fun startApiService() {
        val modelId = CurrentModelManager.getCurrentModelId()
        if (modelId.isNullOrBlank()) {
            Toast.makeText(this, R.string.console_no_model_hint, Toast.LENGTH_LONG).show()
            binding.switchService.isChecked = false
            return
        }
        val modelFile = ModelDownloadManager.getInstance(this).getDownloadedFile(modelId)
        if (modelFile == null) {
            Toast.makeText(this, R.string.console_model_missing_hint, Toast.LENGTH_LONG).show()
            binding.switchService.isChecked = false
            return
        }
        // Keep the auto-start config in sync so the native settings screen
        // (enable_api_service) agrees with the console's live state.
        PreferenceManager.getDefaultSharedPreferences(this)
            .edit().putBoolean("enable_api_service", true).apply()
        ApiServiceManager.startApiService(this, modelId)
    }

    /**
     * Pick the service model from the list of downloaded models. The console
     * must not hard-code a single model: users swap or delete models freely.
     */
    private fun showModelPicker() {
        // Stable initial list: download-history models only. Filesystem scan
        // is opt-in via the Refresh button so the list doesn't change under
        // the user (e.g. stray cache dirs appearing).
        val downloadManager = ModelDownloadManager.getInstance(this)
        val dbModels = ChatDataManager.getInstance(this).getAllDownloadedModels()
            .map { it.modelId }
            .filter { downloadManager.getDownloadedFile(it) != null }
        showModelPickerDialog(dbModels)
    }

    private fun showModelPickerDialog(models: List<String>) {
        if (models.isEmpty()) {
            Toast.makeText(this, R.string.console_no_model_hint, Toast.LENGTH_LONG).show()
            return
        }
        val names = models.map { displayNameOf(it) }
        AlertDialog.Builder(this)
            .setTitle(R.string.console_select_model)
            .setItems(names.toTypedArray()) { _, which ->
                val modelId = models[which]
                CurrentModelManager.setCurrentModelId(modelId)
                refresh()
                Toast.makeText(this, R.string.console_model_selected, Toast.LENGTH_SHORT).show()
            }
            .setNeutralButton(R.string.console_clear_model) { _, _ ->
                CurrentModelManager.clearCurrentModelId()
                refresh()
            }
            .setPositiveButton(R.string.console_refresh) { _, _ ->
                // Manual full rescan (DB + filesystem), dedup by display name.
                showModelPickerDialog(collectInstalledModels())
            }
            .show()
    }

    private fun showCrashLogs() {
        val files = CrashUtil.getCrashLogFiles()
        if (files.isEmpty()) {
            Toast.makeText(this, R.string.console_no_crash_logs, Toast.LENGTH_SHORT).show()
            return
        }
        val fmt = SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault())
        val names = files.map { "${it.name}  ${fmt.format(Date(it.lastModified()))}" }
        AlertDialog.Builder(this)
            .setTitle(R.string.console_crash_logs)
            .setItems(names.toTypedArray()) { _, which ->
                AlertDialog.Builder(this)
                    .setTitle(files[which].name)
                    .setMessage(files[which].readText().take(3000))
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun displayNameOf(modelId: String): String =
        modelId.substringAfterLast('/').removePrefix("models--MNN--")

    /**
     * Union of download-history models (with files present) and models found
     * on disk — users often copy model folders onto the device manually, and
     * those never appear in the download history DB.
     */
    private fun collectInstalledModels(): List<String> {
        val ids = LinkedHashSet<String>()
        val seenNames = mutableSetOf<String>()
        val downloadManager = ModelDownloadManager.getInstance(this)
        ChatDataManager.getInstance(this).getAllDownloadedModels()
            .map { it.modelId }
            .filter { downloadManager.getDownloadedFile(it) != null }
            .forEach {
                ids.add(it)
                seenNames.add(displayNameOf(it))
            }
        val base = File(filesDir, ".mnnmodels/modelscope")
        base.listFiles()?.forEach { dir ->
            if (!dir.isDirectory) return@forEach
            // Both flat layout (LFM2-350M-MNN/llm.mnn) and HF-style cache
            // (models--X--Y/snapshots/_no_sha_/llm.mnn) are in the wild.
            val hasModel = dir.walkTopDown().maxDepth(3).any {
                it.isFile && (it.name == "llm.mnn" || it.name == "config.json")
            }
            if (hasModel) {
                val displayName = displayNameOf("x/${dir.name}")
                if (displayName in seenNames) return@forEach
                val candidate = "ModelScope/MNN/${dir.name}"
                if (downloadManager.getDownloadedFile(candidate) != null) {
                    ids.add(candidate)
                    seenNames.add(displayName)
                }
            }
        }
        return ids.toList()
    }

    private fun stopApiService() {
        ApiServiceManager.stopApiService(this)
        PreferenceManager.getDefaultSharedPreferences(this)
            .edit().putBoolean("enable_api_service", false).apply()
    }

    private fun refresh() {
        val port = ApiServerConfig.getPort(this)
        val ip = getLocalIpAddress()
        val running = ServerEventManager.getInstance().isServerRunning()
        val modelId = CurrentModelManager.getCurrentModelId()

        binding.textServiceStatus.text =
            if (running) getString(R.string.console_status_running) else getString(R.string.console_status_stopped)
        binding.textServiceStatus.setTextColor(
            if (running) Color.rgb(0x4C, 0xAF, 0x50) else Color.rgb(0x9E, 0x9E, 0x9E)
        )
        // Detach listener while syncing UI state, otherwise refresh() re-triggers
        // start/stop through the switch and fights with the server state flow.
        binding.switchService.setOnCheckedChangeListener(null)
        if (binding.switchService.isChecked != running) {
            binding.switchService.isChecked = running
        }
        binding.switchService.setOnCheckedChangeListener(serviceSwitchListener)
        binding.textModelName.text = modelId ?: getString(R.string.console_no_model)
        binding.textDeviceRam.text = ramHint()
        if (modelId != null &&
            ModelDownloadManager.getInstance(this).getDownloadedFile(modelId) == null
        ) {
            binding.textModelName.text = getString(R.string.console_model_missing, modelId.substringAfterLast('/'))
        }
        // Show the address matching what the server actually binds to.
        val configuredIp = ApiServerConfig.getIpAddress(this)
        binding.textAddress.text = when {
            !running -> getString(R.string.console_service_not_started)
            configuredIp == "0.0.0.0" -> "http://$ip:$port"
            configuredIp == "127.0.0.1" -> getString(R.string.console_address_local_only, port)
            else -> "http://$configuredIp:$port"
        }
        binding.textApiKey.text =
            if (ApiServerConfig.isAuthEnabled(this)) ApiServerConfig.getApiKey(this)
            else getString(R.string.console_auth_disabled)
    }

    /** Device RAM with a model-size recommendation. */
    private fun ramHint(): String {
        val mi = ActivityManager.MemoryInfo()
        (getSystemService(ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(mi)
        val gb = mi.totalMem / 1024.0 / 1024.0 / 1024.0
        val limit = when {
            gb < 2.0 -> "1B"
            gb < 4.0 -> "2B"
            gb < 8.0 -> "3B"
            else -> "∞"
        }
        return getString(R.string.console_ram_hint, String.format("%.1fGB", gb), limit)
    }

    private fun copyToClipboard(text: String) {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("api_key", text))
    }

    private fun getLocalIpAddress(): String {
        return try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            for (networkInterface in interfaces) {
                if (networkInterface.isLoopback || !networkInterface.isUp) continue
                for (address in Collections.list(networkInterface.inetAddresses)) {
                    if (!address.isLoopbackAddress && address.hostAddress?.contains(":") == false) {
                        return address.hostAddress ?: "0.0.0.0"
                    }
                }
            }
            "0.0.0.0"
        } catch (e: Exception) {
            "0.0.0.0"
        }
    }
}
