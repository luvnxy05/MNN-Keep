package com.alibaba.mnnllm.android.console

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.alibaba.mnnllm.android.R

/**
 * Vendor ROM keep-alive guide: detect the ROM and show the exact whitelist
 * steps (auto-start / background / battery) needed for unattended operation.
 * Vendor policies differ wildly, so this is the reliable path — unlike grey
 * tricks such as fake location/audio that ROMs routinely block.
 */
object KeepAliveGuide {

    fun detectRom(): String {
        val brand = (Build.MANUFACTURER ?: "").lowercase()
        val model = (Build.BRAND ?: "").lowercase()
        return when {
            brand.contains("xiaomi") || brand.contains("redmi") || model.contains("xiaomi") -> "Xiaomi / HyperOS"
            brand.contains("huawei") || brand.contains("honor") || brand.contains("harmony") -> "Huawei HarmonyOS / Honor MagicOS"
            brand.contains("oppo") || brand.contains("realme") || brand.contains("oneplus") -> "OPPO / OnePlus (ColorOS)"
            brand.contains("vivo") || brand.contains("iqoo") -> "vivo (OriginOS)"
            brand.contains("samsung") -> "Samsung One UI"
            else -> "Other Android"
        }
    }

    private fun stepsFor(rom: String): Array<Pair<String, String>> {
        return when (rom) {
            "Xiaomi / HyperOS" -> arrayOf(
                "自启动" to "设置 → 应用设置 → 应用管理 → MNN Keep → 自启动 → 允许",
                "省电策略" to "设置 → 应用设置 → 应用管理 → MNN Keep → 省电策略 → 无限制",
                "后台弹出界面" to "同上页面 → 后台弹出界面 → 允许"
            )
            "Huawei HarmonyOS / Honor MagicOS" -> arrayOf(
                "应用启动管理" to "设置 → 应用 → 应用管理 → MNN Keep → 应用启动管理 → 手动管理 → 全允许（自启动/关联启动/后台活动）",
                "耗电保护" to "设置 → 电池 → 更多电池设置 → 耗电保护 → MNN Keep → 不限制"
            )
            "OPPO / OnePlus (ColorOS)" -> arrayOf(
                "自启动" to "设置 → 应用管理 → MNN Keep → 自启动 → 允许",
                "后台运行" to "设置 → 应用管理 → MNN Keep → 耗电管理 → 允许后台运行"
            )
            "vivo (OriginOS)" -> arrayOf(
                "后台耗电管理" to "设置 → 电池 → 后台耗电管理 → MNN Keep → 允许后台",
                "自启动" to "设置 → 应用与权限 → 权限管理 → 自启动 → 允许 MNN Keep"
            )
            else -> arrayOf(
                "一般无需特殊设置" to "原生/类原生系统（LineageOS、Pixel 等）默认允许前台服务与自启动",
                "如后台被杀" to "系统设置 → 应用 → MNN Keep → 电池 → 无限制/不优化"
            )
        }
    }

    /** Show the guide dialog; returns the scrollable view for reuse. */
    fun show(activity: Activity) {
        val rom = detectRom()
        val sb = StringBuilder()
        stepsFor(rom).forEach { (title, detail) ->
            sb.append("• ").append(title).append('\n').append("  ").append(detail).append("\n\n")
        }
        sb.append(activity.getString(R.string.keepalive_adb_hint))

        val text = TextView(activity).apply {
            setPadding(48, 32, 48, 16)
            textSize = 14f
            text = sb.toString()
        }
        val scroll = ScrollView(activity).apply { addView(text) }

        AlertDialog.Builder(activity)
            .setTitle(activity.getString(R.string.keepalive_guide_title, rom))
            .setView(scroll)
            .setPositiveButton(R.string.keepalive_open_app_settings) { _, _ ->
                try {
                    activity.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                            .setData(android.net.Uri.parse("package:${activity.packageName}"))
                    )
                } catch (_: Exception) { }
            }
            .setNeutralButton(R.string.keepalive_battery_optimization) { _, _ ->
                try {
                    activity.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                } catch (_: Exception) { }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}
