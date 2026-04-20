package com.dawn.audio;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.SoundPool;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RawRes;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * 短音效播放器 — 基于 SoundPool，适合播放简短的音效（点击音、提示音、倒计时数字等）。
 * <p>
 * 特点：低延迟、支持同时播放多个音效、预加载后即时触发。
 * <p>
 * 使用示例：
 * <pre>
 *   SoundEffectPlayer player = new SoundEffectPlayer();
 *   player.init(context, 4); // 最多同时播放4个音效
 *   player.loadFromResource("click", R.raw.click);
 *   player.loadFromResource("beep", R.raw.beep);
 *   player.play("click");
 *   // ...
 *   player.release();
 * </pre>
 */
public class SoundEffectPlayer {

    private static final String TAG = "SoundEffectPlayer";

    @Nullable
    private Context appContext;
    @Nullable
    private SoundPool soundPool;
    private final Map<String, Integer> soundIdMap = new HashMap<>();
    private float volume = 1.0f;

    /**
     * 初始化 SoundPool。
     *
     * @param context    Context
     * @param maxStreams 最大同时播放数（建议 2~6）
     */
    public void init(@NonNull Context context, int maxStreams) {
        appContext = context.getApplicationContext();
        release();

        AudioAttributes attrs = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setLegacyStreamType(AudioManager.STREAM_MUSIC)
                .build();

        soundPool = new SoundPool.Builder()
                .setMaxStreams(Math.max(1, maxStreams))
                .setAudioAttributes(attrs)
                .build();
    }

    /**
     * 从 raw 资源加载音效。
     *
     * @param key   标识名（用于 play 时指定）
     * @param rawId raw 资源 ID，如 R.raw.click
     */
    public void loadFromResource(@NonNull String key, @RawRes int rawId) {
        SoundPool pool = soundPool;
        Context ctx = appContext;
        if (pool == null || ctx == null) {
            Log.w(TAG, "not initialized");
            return;
        }
        int id = pool.load(ctx, rawId, 1);
        soundIdMap.put(key, id);
    }

    /**
     * 从 assets 目录加载音效。
     *
     * @param key       标识名
     * @param assetPath assets 下的路径，如 "sounds/click.ogg"
     */
    public void loadFromAsset(@NonNull String key, @NonNull String assetPath) {
        SoundPool pool = soundPool;
        Context ctx = appContext;
        if (pool == null || ctx == null) {
            Log.w(TAG, "not initialized");
            return;
        }
        AssetFileDescriptor afd = null;
        try {
            afd = ctx.getAssets().openFd(assetPath);
            int id = pool.load(afd, 1);
            soundIdMap.put(key, id);
        } catch (IOException e) {
            Log.e(TAG, "load asset failed: " + assetPath, e);
        } finally {
            if (afd != null) {
                try { afd.close(); } catch (IOException ignore) {}
            }
        }
    }

    /**
     * 播放已加载的音效（默认音量、默认速率）。
     *
     * @param key 之前加载时使用的标识名
     */
    public void play(@NonNull String key) {
        play(key, volume, 1.0f);
    }

    /**
     * 播放已加载的音效（自定义音量和速率）。
     *
     * @param key    标识名
     * @param volume 音量 (0.0 ~ 1.0)
     * @param rate   播放速率 (0.5 ~ 2.0)
     */
    public void play(@NonNull String key, float volume, float rate) {
        SoundPool pool = soundPool;
        if (pool == null) return;
        Integer id = soundIdMap.get(key);
        if (id == null) {
            Log.w(TAG, "sound not loaded: " + key);
            return;
        }
        float vol = Math.max(0f, Math.min(1f, volume));
        float r = Math.max(0.5f, Math.min(2.0f, rate));
        pool.play(id, vol, vol, 1, 0, r);
    }

    /**
     * 设置默认播放音量。
     */
    public void setVolume(float volume) {
        this.volume = Math.max(0f, Math.min(1f, volume));
    }

    /**
     * 暂停所有正在播放的音效。
     */
    public void pauseAll() {
        SoundPool pool = soundPool;
        if (pool != null) {
            pool.autoPause();
        }
    }

    /**
     * 恢复所有暂停的音效。
     */
    public void resumeAll() {
        SoundPool pool = soundPool;
        if (pool != null) {
            pool.autoResume();
        }
    }

    /**
     * 卸载指定音效。
     */
    public void unload(@NonNull String key) {
        SoundPool pool = soundPool;
        Integer id = soundIdMap.remove(key);
        if (pool != null && id != null) {
            pool.unload(id);
        }
    }

    /**
     * 释放所有资源。
     */
    public void release() {
        SoundPool pool = soundPool;
        soundPool = null;
        soundIdMap.clear();
        if (pool != null) {
            pool.release();
        }
    }

    /**
     * 是否已初始化。
     */
    public boolean isInitialized() {
        return soundPool != null;
    }
}
