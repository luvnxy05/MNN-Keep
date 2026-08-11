# MNN Keep

[English](README_EN.md) | **中文**

**把旧安卓手机变成无人值守的本地大模型推理服务器。**

MNN Keep 是基于 [alibaba/MNN](https://github.com/alibaba/MNN) 的 MNN-Chat 深度改造的 Android 应用，定位为**推理服务机**而非聊天玩具：内置 OpenAI 兼容 API 服务，可被 HomeAssistant、脚本、其他应用通过 HTTP 调用；针对 7×24 无人值守场景做了完整的保活与自愈设计。

## 特性

- **OpenAI 兼容 API**：`/v1/chat/completions`、`/v1/models`，支持 Bearer 认证、CORS、Anthropic 兼容端点，局域网内任意设备可调用
- **服务控制台**：启动即见服务状态、模型、端口、API Key、一键启停；模型自选（下载历史 + 文件扫描，手动刷新）
- **保活四件套**（无人值守核心）：
  - 前台服务 + 常驻通知（Android 10-15 兼容，含 Android 15 FGS 启动限制适配）
  - `START_STICKY` 系统杀后自动重建，模型配置持久化自动恢复
  - **崩溃自愈**：未捕获异常 3 秒后自动拉起（AlarmManager，无需额外权限）
  - **开机自启**：BOOT_COMPLETED 两段式启动，避开 Android 15 开机 FGS 限制
- **隐私优先**：无 Firebase、无任何云端依赖，崩溃日志只存本机，模型推理完全离线
- **模型生态**：内置 ModelScope 模型市场（Qwen3.5/LFM2/Gemma 等 4bit 预转换模型直接下载）

## 快速开始

1. 从 [Releases](../../releases) 下载 APK 安装（或在模型市场直接下载）
2. 打开 App → 「模型市场」下载一个模型（推荐 Qwen3.5-0.8B 起步）
3. 回到「服务控制台」→ 选择模型 → 打开服务开关
4. 局域网访问：`http://<手机IP>:8080/v1/chat/completions`（API Key 在控制台查看）

```bash
curl -H "Authorization: Bearer <API_KEY>" \
     -H "Content-Type: application/json" \
     -d '{"model":"ModelScope/MNN/Qwen3.5-0.8B-MNN","messages":[{"role":"user","content":"你好"}]}' \
     http://192.168.1.100:8080/v1/chat/completions
```

### HomeAssistant 集成

HA 通过 OpenAI 兼容对话集成调用：

```yaml
# configuration.yaml
openai_conversation:
  api_key: "<API_KEY>"
  base_url: "http://192.168.1.100:8080/v1"
  model: "ModelScope/MNN/Qwen3.5-0.8B-MNN"
```

或使用 REST 命令 / shell_command 调用。

## 无人值守部署指南（重要）

### 1. 锁屏（FBE）——最大的坑
带 PIN/图案/密码锁的设备**重启后凭据区（CE）锁定**，任何 app 的开机自启/崩溃自愈都会失效（组件报 `Activity does not exist`）。
**部署时必须：设置 → 改为滑动锁或无锁屏**，否则断电重启后需要人工解锁一次。

### 2. 厂商白名单（HyperOS / 鸿蒙 / MagicOS / ColorOS）
各厂商省电策略会拦截自启动和后台：设置 → 应用启动管理 → 允许**自启动、关联启动、后台活动**，并关闭电池优化。

### 3. ADB 加固（可选，一劳永逸）
```bash
adb shell dumpsys deviceidle whitelist +io.mnnkeep.app   # 电池白名单
```

### 4. 物理兜底
智能插座 + 手机「充电自动开机」= 硬件级 watchdog：服务心跳超时 → 断电重启。

### 5. 手动放置模型
拷贝模型目录到 `/data/data/io.mnnkeep.app/files/.mnnmodels/modelscope/` 时**必须 flat 结构**（`llm.mnn`/`config.json` 直接在该目录下）。HF 缓存结构（`models--X--Y/snapshots/...`）无法被服务加载（`MODEL_CONFIG_NOT_FOUND`），请用 App 内模型市场下载。

## 自编译

依赖 MNN 引擎（[alibaba/MNN](https://github.com/alibaba/MNN) 仓库），先编译引擎再编 App：

```bash
# 1. 编译 MNN 引擎（arm64，含 LLM/vision/audio）
cd MNN/project/android && mkdir build_64 && cd build_64
../build_64.sh "-DMNN_LOW_MEMORY=true -DMNN_BUILD_LLM=true -DMNN_ARM82=true -DMNN_OPENCL=true \
-DLLM_SUPPORT_VISION=true -DMNN_BUILD_OPENCV=true -DMNN_IMGCODECS=true -DLLM_SUPPORT_AUDIO=true \
-DMNN_BUILD_AUDIO=true -DMNN_BUILD_DIFFUSION=ON -DMNN_SEP_BUILD=OFF \
-DCMAKE_SHARED_LINKER_FLAGS='-Wl,-z,max-page-size=16384' -DCMAKE_INSTALL_PREFIX=."
make install

# 2. 编译 App
cd apps/Android/MnnLlmChat
export ANDROID_NDK=$HOME/Library/Android/sdk/ndk/27.0.12077973
./gradlew :app:assembleStandardDebug
```

## 与上游的关系

本项目是 [MNN-Chat](https://github.com/alibaba/MNN/tree/master/apps/Android/MnnLlmChat) 的 fork，Apache 2.0 许可。核心改造：

- **保活**：START_STICKY、崩溃自愈、开机自启（BootReceiver）、模型配置持久化
- **服务控制台**：全新首页（状态/模型/端口/API Key/启停开关）
- **修复上游 Bug**（真机 Android 15 验证）：
  - `ForegroundServiceDidNotStartInTimeException`（下载服务 5 秒前台化竞态，Android 15 必现）
  - 下载失败与 stopService 竞态崩溃
  - 停服务清空模型配置导致的「无法重启服务」死循环
  - jitpack 依赖本地化（WaveRecorder/DeviceName 源码内嵌，Markwon 换 maven central 原版）
- **隐私**：移除 Firebase Analytics/Crashlytics

## 已知限制

- 麒麟 Mali GPU 跑 LLM decode 不可靠（OpenCL 动态 shape 问题），Kirin 设备 CPU-only
- 各厂商 ROM 保活行为差异大，参考上文部署指南逐项授权
- 官方仅保证高配机型体验；2GB 以下内存设备不建议跑 2B+ 模型

## License

[Apache License 2.0](LICENSE)（与上游 MNN 一致）。模型版权归各模型厂商，见 ModelScope 各模型页。
