package com.dawn.audio;

import android.media.audiofx.Equalizer;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Arrays;

/**
 * 均衡器管理器 — 管理音频均衡器（12 频段 EQ），支持预设和自定义频段调节。
 * <p>
 * 需要绑定到 MediaPlayer 的 audioSessionId 才能生效。
 * <p>
 * 使用示例：
 * <pre>
 *   EqualizerManager eq = new EqualizerManager();
 *   eq.init(musicPlayer.getAudioSessionId());
 *   eq.applyPreset(EqualizerManager.PRESET_BASS);
 *   // 或自定义
 *   eq.setBandLevelDb(0, 3.0f);  // 32Hz +3dB
 *   // ...
 *   eq.release();
 * </pre>
 */
public class EqualizerManager {

    private static final String TAG = "EqualizerManager";

    // ===== 预设名称 =====
    public static final String PRESET_FLAT = "flat";
    public static final String PRESET_VOCAL = "vocal";
    public static final String PRESET_BASS = "bass";
    public static final String PRESET_BRIGHT = "bright";
    public static final String PRESET_RECOMMENDED = "recommended";

    public static final float MIN_GAIN_DB = -12.0f;
    public static final float MAX_GAIN_DB = 12.0f;

    /** 12 频段中心频率 (Hz) */
    private static final int[] BAND_FREQUENCIES_HZ = {
            32, 64, 125, 250,
            500, 1000, 2000, 4000,
            6000, 8000, 12000, 16000
    };

    // ===== 预设频段增益 (dB) =====
    private static final float[] FLAT_DB = {
            0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f
    };
    private static final float[] RECOMMENDED_DB = {
            1.0f, 0.7f, 0.3f, 0.0f,
            -0.2f, 0.0f, 0.4f, 0.8f,
            0.9f, 0.7f, 0.3f, 0.0f
    };
    private static final float[] VOCAL_DB = {
            -3.0f, -2.0f, -1.0f, -0.2f,
            0.5f, 1.2f, 2.0f, 1.8f,
            0.8f, 0.1f, -0.4f, -0.8f
    };
    private static final float[] BASS_DB = {
            3.2f, 2.7f, 1.8f, 0.8f,
            0.2f, -0.1f, -0.2f, 0.0f,
            0.1f, 0.0f, -0.2f, -0.3f
    };
    private static final float[] BRIGHT_DB = {
            -1.0f, -0.8f, -0.5f, -0.2f,
            0.0f, 0.2f, 0.8f, 1.4f,
            1.7f, 1.5f, 0.9f, 0.4f
    };

    @Nullable
    private Equalizer equalizer;
    private int sessionId;
    private boolean enabled = true;
    @NonNull
    private float[] bandLevelsDb = Arrays.copyOf(RECOMMENDED_DB, RECOMMENDED_DB.length);
    @NonNull
    private String currentPreset = PRESET_RECOMMENDED;

    /**
     * 初始化均衡器，绑定到指定的音频 Session ID。
     *
     * @param audioSessionId MediaPlayer.getAudioSessionId() 返回的值
     */
    public void init(int audioSessionId) {
        if (audioSessionId <= 0) {
            Log.w(TAG, "invalid audioSessionId: " + audioSessionId);
            return;
        }
        release();
        try {
            equalizer = new Equalizer(0, audioSessionId);
            sessionId = audioSessionId;
            applySettings();
            Log.i(TAG, "initialized with sessionId=" + audioSessionId
                    + ", hardwareBands=" + equalizer.getNumberOfBands());
        } catch (Exception e) {
            Log.e(TAG, "init failed", e);
            release();
        }
    }

    /**
     * 重新绑定到新的 Session ID（如 MediaPlayer 重建后）。
     */
    public void rebind(int audioSessionId) {
        if (audioSessionId <= 0 || audioSessionId == sessionId) return;
        init(audioSessionId);
    }

    /**
     * 是否已启用均衡器。
     */
    public boolean isEnabled() { return enabled; }

    /**
     * 启用/禁用均衡器。
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        Equalizer eq = equalizer;
        if (eq != null) {
            try { eq.setEnabled(enabled); } catch (Exception e) {
                Log.e(TAG, "setEnabled failed", e);
            }
        }
    }

    /**
     * 获取可用预设名称列表。
     */
    @NonNull
    public static String[] getAvailablePresets() {
        return new String[]{PRESET_FLAT, PRESET_RECOMMENDED, PRESET_VOCAL, PRESET_BASS, PRESET_BRIGHT};
    }

    /**
     * 应用预设。
     *
     * @param presetName 预设名称，如 {@link #PRESET_BASS}
     */
    public void applyPreset(@NonNull String presetName) {
        currentPreset = presetName;
        bandLevelsDb = copyPresetLevels(presetName);
        enabled = true;
        applySettings();
    }

    /**
     * 获取当前预设名称。
     */
    @NonNull
    public String getCurrentPreset() { return currentPreset; }

    /**
     * 获取 12 频段中心频率数组 (Hz)。
     */
    @NonNull
    public static int[] getBandFrequenciesHz() {
        return Arrays.copyOf(BAND_FREQUENCIES_HZ, BAND_FREQUENCIES_HZ.length);
    }

    /**
     * 获取频段数量。
     */
    public static int getBandCount() {
        return BAND_FREQUENCIES_HZ.length;
    }

    /**
     * 获取指定频段的中心频率。
     */
    public static int getBandFrequencyHz(int bandIndex) {
        return BAND_FREQUENCIES_HZ[bandIndex];
    }

    /**
     * 格式化频率标签，如 "1 kHz" 或 "250 Hz"。
     */
    @NonNull
    public static String formatFrequencyLabel(int frequencyHz) {
        if (frequencyHz < 1000) return frequencyHz + " Hz";
        if (frequencyHz % 1000 == 0) return (frequencyHz / 1000) + " kHz";
        return String.format(java.util.Locale.US, "%.1f kHz", frequencyHz / 1000.0f);
    }

    /**
     * 获取当前所有频段增益值 (dB)。
     */
    @NonNull
    public float[] getBandLevelsDb() {
        return Arrays.copyOf(bandLevelsDb, bandLevelsDb.length);
    }

    /**
     * 设置所有频段增益值 (dB)。
     *
     * @param levels 12 个浮点数，范围 [-12, +12] dB
     */
    public void setBandLevelsDb(@NonNull float[] levels) {
        int count = Math.min(bandLevelsDb.length, levels.length);
        for (int i = 0; i < count; i++) {
            bandLevelsDb[i] = clampDb(levels[i]);
        }
        currentPreset = resolvePreset(bandLevelsDb);
        applySettings();
    }

    /**
     * 设置单个频段增益值 (dB)。
     *
     * @param bandIndex 频段索引 (0~11)
     * @param levelDb   增益值 (-12 ~ +12 dB)
     */
    public void setBandLevelDb(int bandIndex, float levelDb) {
        if (bandIndex < 0 || bandIndex >= bandLevelsDb.length) return;
        bandLevelsDb[bandIndex] = clampDb(levelDb);
        currentPreset = resolvePreset(bandLevelsDb);
        applySettings();
    }

    /**
     * 获取指定频段当前增益值。
     */
    public float getBandLevelDb(int bandIndex) {
        if (bandIndex < 0 || bandIndex >= bandLevelsDb.length) return 0f;
        return bandLevelsDb[bandIndex];
    }

    /**
     * 将当前频段设置编码为逗号分隔字符串（用于持久化）。
     */
    @NonNull
    public String encodeBandLevels() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < bandLevelsDb.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(bandLevelsDb[i]);
        }
        return sb.toString();
    }

    /**
     * 从逗号分隔字符串恢复频段设置。
     */
    public void decodeBandLevels(@Nullable String encoded) {
        if (encoded == null || encoded.trim().isEmpty()) return;
        String[] parts = encoded.split(",");
        int count = Math.min(bandLevelsDb.length, parts.length);
        for (int i = 0; i < count; i++) {
            try {
                bandLevelsDb[i] = clampDb(Float.parseFloat(parts[i].trim()));
            } catch (NumberFormatException e) {
                return;
            }
        }
        currentPreset = resolvePreset(bandLevelsDb);
        applySettings();
    }

    /**
     * 对给定的目标频率进行线性插值，返回插值后的增益值。
     *
     * @param targetFrequencyHz 目标频率 (Hz)
     * @return 插值后的增益 (dB)
     */
    public float interpolateBandLevelDb(int targetFrequencyHz) {
        if (targetFrequencyHz <= BAND_FREQUENCIES_HZ[0]) return bandLevelsDb[0];
        int last = BAND_FREQUENCIES_HZ.length - 1;
        if (targetFrequencyHz >= BAND_FREQUENCIES_HZ[last]) return bandLevelsDb[last];

        double targetLog = Math.log(targetFrequencyHz);
        for (int i = 1; i < BAND_FREQUENCIES_HZ.length; i++) {
            if (targetFrequencyHz > BAND_FREQUENCIES_HZ[i]) continue;
            double leftLog = Math.log(BAND_FREQUENCIES_HZ[i - 1]);
            double rightLog = Math.log(BAND_FREQUENCIES_HZ[i]);
            double span = rightLog - leftLog;
            float ratio = span <= 0 ? 0f : (float) ((targetLog - leftLog) / span);
            return bandLevelsDb[i - 1] + (bandLevelsDb[i] - bandLevelsDb[i - 1]) * ratio;
        }
        return bandLevelsDb[last];
    }

    /**
     * 释放均衡器资源。
     */
    public void release() {
        Equalizer eq = equalizer;
        equalizer = null;
        sessionId = 0;
        if (eq != null) {
            try { eq.release(); } catch (Exception ignore) {}
        }
    }

    // ======================== Internal ========================

    private void applySettings() {
        Equalizer eq = equalizer;
        if (eq == null) return;
        try {
            eq.setEnabled(enabled);
            if (!enabled) return;

            short[] levelRange = eq.getBandLevelRange();
            int minLevel = (levelRange != null && levelRange.length > 0) ? levelRange[0] : -1500;
            int maxLevel = (levelRange != null && levelRange.length > 1) ? levelRange[1] : 1500;
            short hardwareBands = eq.getNumberOfBands();

            for (short band = 0; band < hardwareBands; band++) {
                int centerHz = eq.getCenterFreq(band) / 1000;
                float levelDb = interpolateBandLevelDb(centerHz);
                int millibel = Math.round(levelDb * 100f);
                millibel = Math.max(minLevel, Math.min(maxLevel, millibel));
                eq.setBandLevel(band, (short) millibel);
            }
        } catch (Exception e) {
            Log.e(TAG, "apply settings failed", e);
            release();
        }
    }

    @NonNull
    private static float[] copyPresetLevels(@NonNull String presetName) {
        switch (presetName) {
            case PRESET_FLAT: return Arrays.copyOf(FLAT_DB, FLAT_DB.length);
            case PRESET_VOCAL: return Arrays.copyOf(VOCAL_DB, VOCAL_DB.length);
            case PRESET_BASS: return Arrays.copyOf(BASS_DB, BASS_DB.length);
            case PRESET_BRIGHT: return Arrays.copyOf(BRIGHT_DB, BRIGHT_DB.length);
            case PRESET_RECOMMENDED:
            default: return Arrays.copyOf(RECOMMENDED_DB, RECOMMENDED_DB.length);
        }
    }

    @NonNull
    private static String resolvePreset(@NonNull float[] levels) {
        if (matchesPreset(levels, FLAT_DB)) return PRESET_FLAT;
        if (matchesPreset(levels, RECOMMENDED_DB)) return PRESET_RECOMMENDED;
        if (matchesPreset(levels, VOCAL_DB)) return PRESET_VOCAL;
        if (matchesPreset(levels, BASS_DB)) return PRESET_BASS;
        if (matchesPreset(levels, BRIGHT_DB)) return PRESET_BRIGHT;
        return "custom";
    }

    private static boolean matchesPreset(@NonNull float[] levels, @NonNull float[] preset) {
        int count = Math.min(levels.length, preset.length);
        if (levels.length != preset.length) return false;
        for (int i = 0; i < count; i++) {
            if (Math.abs(levels[i] - preset[i]) > 0.3f) return false;
        }
        return true;
    }

    private static float clampDb(float db) {
        return Math.max(MIN_GAIN_DB, Math.min(MAX_GAIN_DB, db));
    }
}
