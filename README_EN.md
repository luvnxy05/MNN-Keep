# MNN Keep

**中文** | [English](README_EN.md)

**Turn an old Android phone into an unattended on-device LLM inference server.**

MNN Keep is a deep fork of [MNN-Chat](https://github.com/alibaba/MNN/tree/master/apps/Android/MnnLlmChat) (from [alibaba/MNN](https://github.com/alibaba/MNN)), rebuilt as an **inference appliance** rather than a chat toy: it ships an OpenAI-compatible API server (callable by HomeAssistant, scripts, anything over HTTP) plus a complete keep-alive / self-healing stack for 24×7 headless operation.

## Features

- **OpenAI-compatible API**: `/v1/chat/completions`, `/v1/models`, Bearer auth, CORS, Anthropic-compatible endpoints — reachable from any device on your LAN
- **Service console**: home screen shows service state, model, port, API key, one-tap start/stop; pick your model from download history + disk scan (manual refresh), **hot-swap models while the server keeps running**
- **Ops panel**: request stats (count / success rate / tokens, persisted across restarts), uptime, model load time, live API request logs (API console), on-device crash log viewer
- **Deployment aids**: RAM check with model-size recommendation, stay-awake-while-charging (with Android 15 permission fallback guide), vendor keep-alive guide (auto-detects ROM whitelist steps), config backup/restore (JSON, migrate to a new device in one file)
- **Keep-alive stack** (the whole point):
  - Foreground service + persistent notification (Android 10–15, incl. Android 15 FGS launch restrictions)
  - `START_STICKY` auto-rebuild with persisted model config restored
  - **Crash self-healing**: relaunch ~3s after any uncaught exception (AlarmManager, no extra permission)
  - **Boot auto-start**: two-phase BOOT_COMPLETED that dodges the Android 15 boot-FGS ban
- **Privacy-first**: no Firebase, no cloud, crash logs stay on-device, inference fully offline
- **Model ecosystem**: built-in ModelScope market (Qwen3.5 / LFM2 / Gemma 4-bit converted models, direct download)

## Quick start

1. Install the APK from [Releases](../../releases) — or just grab it from the in-app model market
2. Open the app → "模型市场" (Model Market) → download a model (Qwen3.5-0.8B is a good start)
3. Back on the console → pick the model → flip the service switch
4. Call it: `http://<phone-ip>:8080/v1/chat/completions` (API key shown on the console)

```bash
curl -H "Authorization: Bearer <API_KEY>" \
     -H "Content-Type: application/json" \
     -d '{"model":"ModelScope/MNN/Qwen3.5-0.8B-MNN","messages":[{"role":"user","content":"hello"}]}' \
     http://192.168.1.100:8080/v1/chat/completions
```

### HomeAssistant

```yaml
openai_conversation:
  api_key: "<API_KEY>"
  base_url: "http://192.168.1.100:8080/v1"
  model: "ModelScope/MNN/Qwen3.5-0.8B-MNN"
```

## Headless deployment guide (read this)

- **Lock screen (FBE) — the #1 trap**: a PIN/pattern/password lock leaves the credential-encrypted storage **locked after reboot**, so boot auto-start and crash self-healing silently fail (`Activity does not exist`). Use **swipe lock or no lock** on the appliance, or accept a manual unlock after every power loss.
- **Vendor whitelists** (HyperOS / HarmonyOS / MagicOS / ColorOS): allow auto-start, associated-start and background activity in the app-startup manager; disable battery optimization.
- **ADB hardening**: `adb shell dumpsys deviceidle whitelist +io.mnnkeep.app`

- **Manual model folders** must be flat (`llm.mnn`/`config.json` directly in the model dir). HF-cache layout (`models--X--Y/snapshots/...`) fails with `MODEL_CONFIG_NOT_FOUND` — use the in-app market instead.

## Building

Build the MNN engine from [alibaba/MNN](https://github.com/alibaba/MNN) first (see `README_CN.md` for the exact flags), then:

```bash
cd apps/Android/MnnLlmChat
export ANDROID_NDK=$HOME/Library/Android/sdk/ndk/27.0.12077973
./gradlew :app:assembleStandardDebug
```

## Relationship to upstream

Apache 2.0 fork of MNN-Chat. Notable changes: keep-alive stack (START_STICKY / crash self-heal / boot receiver / persisted model), new service console home, fixes for Android 15 `ForegroundServiceDidNotStartInTimeException` crashes and the model-config wipe loop, jitpack deps localized (WaveRecorder/DeviceName inlined, Markwon → maven central), Firebase removed.

## Known limitations

- Mali (Kirin) GPUs are unreliable for LLM decode — CPU-only on Kirin devices
- Vendor ROMs differ wildly on background policy; follow the deployment guide
- Choose models based on your device's performance and RAM; 2GB-RAM devices should stay below 2B-parameter models

## License

[Apache License 2.0](LICENSE) (same as upstream MNN). Model weights are subject to each model's license on ModelScope.
