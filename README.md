# lib-audio

Android 音频工具库，提供音量控制、音效播放、音乐播放、USB 扬声器管理和均衡器调节功能。

## 功能概览

| 模块 | 类名 | 说明 |
|------|------|------|
| 音量控制 | `VolumeManager` | 系统音量读取/设置/调节/静音，不依赖主板类型 |
| 短音效 | `SoundEffectPlayer` | 基于 SoundPool 的低延迟短音效播放 |
| 音乐播放 | `MusicPlayer` | 基于 MediaPlayer 的较长音频播放，支持音频焦点管理 |
| USB 扬声器 | `UsbAudioManager` | USB 音频设备检测、热插拔监听、设备路由 |
| 均衡器 | `EqualizerManager` | 12 频段 EQ 调节，5 种预设 + 自定义 |

## 环境要求

- **minSdk**: 28
- **compileSdk**: 34
- **Java**: 8+

## 依赖引入

```groovy
// settings.gradle
repositories {
    maven { url "https://jitpack.io" }
}

// build.gradle
dependencies {
    implementation 'com.github.baiqingsong:lib-audio:1.0.0'
}
```

---

## VolumeManager — 音量控制

使用系统标准 AudioManager，不依赖主板类型判断。

```java
VolumeManager volumeManager = new VolumeManager();
volumeManager.init(context);

// 获取音量
int vol = volumeManager.getVolume(AudioManager.STREAM_MUSIC);
int max = volumeManager.getMaxVolume(AudioManager.STREAM_MUSIC);
int pct = volumeManager.getVolumePercent(AudioManager.STREAM_MUSIC);

// 设置音量
volumeManager.setVolume(AudioManager.STREAM_MUSIC, 10, true);  // 绝对值
volumeManager.setVolumePercent(AudioManager.STREAM_MUSIC, 80, true); // 百分比

// 调节音量
volumeManager.adjustVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, true);

// 静音
volumeManager.muteStream(AudioManager.STREAM_MUSIC);
volumeManager.unmuteStream(AudioManager.STREAM_MUSIC);
```

| 方法 | 说明 |
|------|------|
| `getVolume(streamType)` | 获取当前音量 |
| `getMaxVolume(streamType)` | 获取最大音量 |
| `setVolume(streamType, volume, showUI)` | 设置绝对音量 |
| `setVolumePercent(streamType, percent, showUI)` | 按百分比设置 |
| `getVolumePercent(streamType)` | 获取百分比 |
| `adjustVolume(streamType, direction, showUI)` | 升降调节 |
| `setMusicVolumeMax(showUI)` | 音乐音量最大 |
| `muteStream(streamType)` / `unmuteStream(streamType)` | 静音/取消 |
| `isStreamMute(streamType)` | 是否静音 |

---

## SoundEffectPlayer — 短音效播放

基于 SoundPool，适合播放点击音、提示音、倒计时数字等短小音效（< 5 秒）。

```java
SoundEffectPlayer player = new SoundEffectPlayer();
player.init(context, 4);  // 最多同时播放 4 个音效

// 加载
player.loadFromResource("click", R.raw.click);
player.loadFromAsset("beep", "sounds/beep.ogg");

// 播放
player.play("click");
player.play("beep", 0.8f, 1.2f);  // 自定义音量和速率

// 释放
player.release();
```

| 方法 | 说明 |
|------|------|
| `init(context, maxStreams)` | 初始化（maxStreams 建议 2~6） |
| `loadFromResource(key, rawId)` | 从 raw 资源加载 |
| `loadFromAsset(key, assetPath)` | 从 assets 加载 |
| `play(key)` | 播放（默认音量） |
| `play(key, volume, rate)` | 播放（自定义音量 0~1，速率 0.5~2） |
| `setVolume(volume)` | 设置默认音量 |
| `pauseAll()` / `resumeAll()` | 暂停/恢复所有 |
| `unload(key)` | 卸载指定音效 |
| `release()` | 释放所有资源 |

---

## MusicPlayer — 音乐播放

基于 MediaPlayer，适合播放较长的音频（背景音乐、语音提示等），自动管理音频焦点。

```java
MusicPlayer player = new MusicPlayer();
player.init(context);
player.setListener(new MusicPlayer.Listener() {
    @Override public void onStart() { }
    @Override public void onComplete() { }
    @Override public void onProgress(long positionMs, long durationMs) { }
});

// 播放方式
player.play("/sdcard/Music/song.mp3", false);      // URI 字符串
player.playFromAssets("music/bgm.mp3", true);       // assets 文件
player.playRaw(R.raw.intro, false);                  // raw 资源

// 控制
player.pause();
player.resume();
player.seekTo(30000);  // 跳转到 30 秒
player.setVolume(0.8f);

// USB 扬声器路由
player.setPreferredDevice(usbDeviceInfo);

// 释放
player.release();
```

### 状态机

```
IDLE → PREPARING → PLAYING ⇄ PAUSED → STOPPED → RELEASED
```

### 音频焦点

- **GAIN**: 恢复播放
- **LOSS**: 暂停
- **LOSS_TRANSIENT**: 暂停，焦点恢复后自动恢复
- **LOSS_TRANSIENT_CAN_DUCK**: 降低音量到 35%

| 方法 | 说明 |
|------|------|
| `play(uriString, looping)` | 播放 URI |
| `playFromAssets(assetPath, looping)` | 播放 assets |
| `playRaw(rawResId, looping)` | 播放 raw 资源 |
| `pause()` / `resume()` / `stop()` | 控制播放 |
| `seekTo(positionMs)` | 跳转位置 |
| `setVolume(volume)` | 设置音量 (0~1) |
| `setPreferredDevice(device)` | 设置输出设备 |
| `getAudioSessionId()` | 获取 Session ID（用于 EQ 绑定） |
| `getState()` | 获取当前状态 |
| `release()` | 释放资源 |

---

## UsbAudioManager — USB 扬声器管理

检测、路由和管理 USB 音频输出设备，支持热插拔监听。

```java
UsbAudioManager usbMgr = new UsbAudioManager();
usbMgr.init(context);

// 监听 USB 设备变化
usbMgr.setDeviceChangeListener(usbDevice -> {
    if (usbDevice != null) {
        musicPlayer.setPreferredDevice(usbDevice);
    } else {
        musicPlayer.setPreferredDevice(null);
    }
});

// 查找 USB 设备
AudioDeviceInfo usb = usbMgr.findUsbAudioOutput();
boolean connected = usbMgr.isUsbAudioConnected();

// 设备信息
String info = usbMgr.listAllOutputDevices();
String desc = UsbAudioManager.describeDevice(usb);

// 释放
usbMgr.release();
```

| 方法 | 说明 |
|------|------|
| `init(context)` | 初始化，开始监听设备变化 |
| `setDeviceChangeListener(listener)` | 设置热插拔回调 |
| `findUsbAudioOutput()` | 查找 USB 音频输出设备 |
| `findPreferredOutput()` | 查找首选输出（USB > 蓝牙 > 有线 > 内置） |
| `isUsbAudioConnected()` | 是否有 USB 音频设备 |
| `getCurrentUsbDevice()` | 获取缓存的 USB 设备 |
| `listAllOutputDevices()` | 列出所有输出设备 |
| `describeDevice(device)` | 静态方法，描述设备信息 |
| `release()` | 取消监听并释放 |

---

## EqualizerManager — 均衡器管理

12 频段 EQ 调节，支持 5 种预设和自定义频段增益。需绑定 MediaPlayer 的 audioSessionId。

```java
EqualizerManager eq = new EqualizerManager();
eq.init(musicPlayer.getAudioSessionId());

// 应用预设
eq.applyPreset(EqualizerManager.PRESET_BASS);

// 自定义频段
eq.setBandLevelDb(0, 3.0f);   // 32Hz +3dB
eq.setBandLevelDb(7, -2.0f);  // 4kHz -2dB

// 获取信息
String preset = eq.getCurrentPreset();
float[] levels = eq.getBandLevelsDb();

// 持久化
String encoded = eq.encodeBandLevels();
eq.decodeBandLevels(encoded);

// 释放
eq.release();
```

### 预设列表

| 预设 | 常量 | 特点 |
|------|------|------|
| 推荐 | `PRESET_RECOMMENDED` | 轻微高低频提升 |
| 平坦 | `PRESET_FLAT` | 所有频段 0 dB |
| 人声 | `PRESET_VOCAL` | 中频突出 |
| 低音 | `PRESET_BASS` | 低频增强 |
| 明亮 | `PRESET_BRIGHT` | 高频增强 |

### 12 频段中心频率

| 索引 | 0 | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 | 9 | 10 | 11 |
|------|---|---|---|---|---|---|---|---|---|---|----|----|
| 频率 | 32Hz | 64Hz | 125Hz | 250Hz | 500Hz | 1kHz | 2kHz | 4kHz | 6kHz | 8kHz | 12kHz | 16kHz |

| 方法 | 说明 |
|------|------|
| `init(audioSessionId)` | 绑定到音频会话 |
| `rebind(audioSessionId)` | 重新绑定（MediaPlayer 重建后） |
| `setEnabled(enabled)` | 启用/禁用 |
| `applyPreset(presetName)` | 应用预设 |
| `setBandLevelDb(index, levelDb)` | 设置单个频段 (-12~+12 dB) |
| `setBandLevelsDb(levels)` | 设置所有频段 |
| `getBandLevelsDb()` | 获取所有频段增益 |
| `encodeBandLevels()` / `decodeBandLevels()` | 序列化/反序列化 |
| `interpolateBandLevelDb(frequencyHz)` | 频率插值 |
| `release()` | 释放资源 |

---

## 典型使用流程

```java
// 1. 初始化
VolumeManager volumeMgr = new VolumeManager();
volumeMgr.init(context);
volumeMgr.setVolumePercent(AudioManager.STREAM_MUSIC, 80, false);

UsbAudioManager usbMgr = new UsbAudioManager();
usbMgr.init(context);

MusicPlayer musicPlayer = new MusicPlayer();
musicPlayer.init(context);

// 2. USB 扬声器路由
AudioDeviceInfo usb = usbMgr.findUsbAudioOutput();
if (usb != null) {
    musicPlayer.setPreferredDevice(usb);
}
usbMgr.setDeviceChangeListener(device ->
    musicPlayer.setPreferredDevice(device)
);

// 3. 短音效
SoundEffectPlayer sfx = new SoundEffectPlayer();
sfx.init(context, 4);
sfx.loadFromResource("click", R.raw.click);
sfx.play("click");

// 4. 音乐播放
musicPlayer.playFromAssets("music/bgm.mp3", true);

// 5. 均衡器
EqualizerManager eq = new EqualizerManager();
eq.init(musicPlayer.getAudioSessionId());
eq.applyPreset(EqualizerManager.PRESET_BASS);

// 6. 释放
eq.release();
musicPlayer.release();
sfx.release();
usbMgr.release();
```

## 注意事项

- 音乐播放和均衡器操作应在**非 UI 线程**执行耗时操作
- `MusicPlayer` 自动管理音频焦点（暂停/恢复/降音量）
- USB 扬声器热插拔回调在主线程执行
- `EqualizerManager` 需在 `MusicPlayer.init()` 后绑定 audioSessionId
- 所有组件使用完毕需调用 `release()` 释放资源
