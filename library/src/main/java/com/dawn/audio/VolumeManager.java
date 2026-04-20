package com.dawn.audio;

import android.content.Context;
import android.media.AudioManager;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * 音量管理器 — 使用系统默认 AudioManager 控制音量，不依赖主板类型。
 * <p>
 * 支持对不同音频流（音乐、铃声、通知、闹钟等）进行独立音量控制。
 */
public class VolumeManager {

    private static final String TAG = "VolumeManager";

    @Nullable
    private AudioManager audioManager;

    /**
     * 初始化。必须在其他方法之前调用。
     */
    public void init(@NonNull Context context) {
        audioManager = (AudioManager) context.getApplicationContext()
                .getSystemService(Context.AUDIO_SERVICE);
    }

    /**
     * 获取指定流的当前音量。
     *
     * @param streamType 音频流类型，如 {@link AudioManager#STREAM_MUSIC}
     * @return 当前音量 (0 ~ maxVolume)，未初始化时返回 0
     */
    public int getVolume(int streamType) {
        AudioManager am = audioManager;
        if (am == null) {
            Log.w(TAG, "not initialized");
            return 0;
        }
        return am.getStreamVolume(streamType);
    }

    /**
     * 获取指定流的最大音量。
     */
    public int getMaxVolume(int streamType) {
        AudioManager am = audioManager;
        if (am == null) return 0;
        return am.getStreamMaxVolume(streamType);
    }

    /**
     * 设置指定流的绝对音量。
     *
     * @param streamType 音频流类型
     * @param volume     音量值 (0 ~ maxVolume)
     * @param showUI     是否显示系统音量条
     */
    public void setVolume(int streamType, int volume, boolean showUI) {
        AudioManager am = audioManager;
        if (am == null) {
            Log.w(TAG, "not initialized");
            return;
        }
        int max = am.getStreamMaxVolume(streamType);
        int clamped = Math.max(0, Math.min(max, volume));
        int flags = showUI ? AudioManager.FLAG_SHOW_UI : 0;
        am.setStreamVolume(streamType, clamped, flags);
    }

    /**
     * 按百分比设置音量。
     *
     * @param streamType 音频流类型
     * @param percent    百分比 (0~100)
     * @param showUI     是否显示系统音量条
     */
    public void setVolumePercent(int streamType, int percent, boolean showUI) {
        AudioManager am = audioManager;
        if (am == null) return;
        int max = am.getStreamMaxVolume(streamType);
        int vol = Math.round(max * Math.max(0, Math.min(100, percent)) / 100f);
        setVolume(streamType, vol, showUI);
    }

    /**
     * 获取当前音量百分比。
     *
     * @return 0~100
     */
    public int getVolumePercent(int streamType) {
        AudioManager am = audioManager;
        if (am == null) return 0;
        int max = am.getStreamMaxVolume(streamType);
        if (max <= 0) return 0;
        return Math.round(am.getStreamVolume(streamType) * 100f / max);
    }

    /**
     * 调节音量（升/降/静音切换）。
     *
     * @param streamType 音频流类型
     * @param direction  {@link AudioManager#ADJUST_RAISE}, {@link AudioManager#ADJUST_LOWER},
     *                   {@link AudioManager#ADJUST_SAME}
     * @param showUI     是否显示系统音量条
     */
    public void adjustVolume(int streamType, int direction, boolean showUI) {
        AudioManager am = audioManager;
        if (am == null) return;
        int flags = showUI ? AudioManager.FLAG_SHOW_UI : 0;
        am.adjustStreamVolume(streamType, direction, flags);
    }

    /**
     * 将音乐流音量设为最大。
     */
    public void setMusicVolumeMax(boolean showUI) {
        setVolume(AudioManager.STREAM_MUSIC, getMaxVolume(AudioManager.STREAM_MUSIC), showUI);
    }

    /**
     * 静音指定流。
     */
    public void muteStream(int streamType) {
        AudioManager am = audioManager;
        if (am == null) return;
        am.adjustStreamVolume(streamType, AudioManager.ADJUST_MUTE, 0);
    }

    /**
     * 取消静音。
     */
    public void unmuteStream(int streamType) {
        AudioManager am = audioManager;
        if (am == null) return;
        am.adjustStreamVolume(streamType, AudioManager.ADJUST_UNMUTE, 0);
    }

    /**
     * 判断指定流是否被静音。
     */
    public boolean isStreamMute(int streamType) {
        AudioManager am = audioManager;
        if (am == null) return false;
        return am.isStreamMute(streamType);
    }
}
