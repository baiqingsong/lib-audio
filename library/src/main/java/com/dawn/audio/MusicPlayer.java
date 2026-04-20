package com.dawn.audio;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.media.AudioAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RawRes;

import java.io.IOException;

/**
 * 音乐播放器 — 基于 MediaPlayer，适合播放较长的音频（背景音乐、语音提示等）。
 * <p>
 * 特点：支持 URI / assets / raw 资源播放，自动管理音频焦点，
 * 支持 USB 扬声器路由、进度回调、暂停恢复。
 * <p>
 * 使用示例：
 * <pre>
 *   MusicPlayer player = new MusicPlayer();
 *   player.init(context);
 *   player.setListener(new MusicPlayer.Listener() {
 *       public void onStart() { }
 *       public void onComplete() { }
 *   });
 *   player.playFromAssets("music/bgm.mp3", true);
 *   // ...
 *   player.release();
 * </pre>
 */
public class MusicPlayer {

    private static final String TAG = "MusicPlayer";
    private static final long DEFAULT_PROGRESS_INTERVAL_MS = 250L;
    private static final float DUCK_VOLUME_FACTOR = 0.35f;

    public enum State {
        IDLE, PREPARING, PLAYING, PAUSED, STOPPED, RELEASED
    }

    public interface Listener {
        default void onStart() {}
        default void onPause() {}
        default void onResume() {}
        default void onStop() {}
        default void onComplete() {}
        default void onProgress(long positionMs, long durationMs) {}
        default void onError(int what, int extra, @Nullable Exception exception) {}
        default void onStateChanged(@NonNull State state) {}
    }

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Nullable
    private Context appContext;
    @Nullable
    private AudioManager audioManager;
    @Nullable
    private MediaPlayer mediaPlayer;
    @Nullable
    private AssetFileDescriptor currentAfd;
    @Nullable
    private Listener listener;
    @Nullable
    private AudioDeviceInfo preferredDevice;
    @Nullable
    private AudioFocusRequest audioFocusRequest;

    private State state = State.IDLE;
    private float volume = 1.0f;
    private boolean hasAudioFocus;
    private boolean pausedByFocusLoss;
    private boolean duckedByFocusLoss;
    private long progressIntervalMs = DEFAULT_PROGRESS_INTERVAL_MS;

    private final Runnable progressRunnable = new Runnable() {
        @Override
        public void run() {
            MediaPlayer mp = mediaPlayer;
            Listener l = listener;
            if (mp == null || l == null) return;
            if (state != State.PLAYING && state != State.PAUSED) return;
            try {
                l.onProgress(mp.getCurrentPosition(), mp.getDuration());
            } catch (IllegalStateException ignore) {}
            if (state == State.PLAYING || state == State.PAUSED) {
                mainHandler.postDelayed(this, progressIntervalMs);
            }
        }
    };

    private final AudioManager.OnAudioFocusChangeListener focusChangeListener = focusChange -> {
        switch (focusChange) {
            case AudioManager.AUDIOFOCUS_GAIN:
                handleFocusGain();
                break;
            case AudioManager.AUDIOFOCUS_LOSS:
                pause();
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                handleTransientLoss();
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                handleDuck();
                break;
        }
    };

    // ======================== Public API ========================

    /**
     * 初始化播放器。
     */
    public void init(@NonNull Context context) {
        appContext = context.getApplicationContext();
        audioManager = (AudioManager) appContext.getSystemService(Context.AUDIO_SERVICE);
        if (state == State.RELEASED) {
            state = State.IDLE;
        }
    }

    public void setListener(@Nullable Listener listener) {
        this.listener = listener;
    }

    public void setProgressIntervalMs(long intervalMs) {
        if (intervalMs > 0) progressIntervalMs = intervalMs;
    }

    /**
     * 设置首选输出设备（用于 USB 扬声器路由）。
     */
    public void setPreferredDevice(@Nullable AudioDeviceInfo device) {
        preferredDevice = device;
        MediaPlayer mp = mediaPlayer;
        if (mp != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            mp.setPreferredDevice(device);
        }
    }

    /**
     * 播放 URI（文件路径、content:// 或 http:// 等）。
     */
    public void play(@NonNull String uriString, boolean looping) {
        if (appContext == null) {
            Log.w(TAG, "not initialized");
            return;
        }
        stopInternal();
        createOrResetPlayer();
        MediaPlayer mp = mediaPlayer;
        if (mp == null) return;
        try {
            mp.setDataSource(appContext, Uri.parse(uriString));
            mp.setLooping(looping);
            prepareAndStart(mp);
        } catch (Exception e) {
            handlePlayError(e);
        }
    }

    /**
     * 播放 Uri 对象。
     */
    public void playUri(@NonNull Uri uri, boolean looping) {
        if (appContext == null) return;
        stopInternal();
        createOrResetPlayer();
        MediaPlayer mp = mediaPlayer;
        if (mp == null) return;
        try {
            mp.setDataSource(appContext, uri);
            mp.setLooping(looping);
            prepareAndStart(mp);
        } catch (Exception e) {
            handlePlayError(e);
        }
    }

    /**
     * 播放 assets 文件。
     */
    public void playFromAssets(@NonNull String assetPath, boolean looping) {
        if (appContext == null) return;
        stopInternal();
        createOrResetPlayer();
        MediaPlayer mp = mediaPlayer;
        if (mp == null) return;
        closeAfd();
        try {
            currentAfd = appContext.getAssets().openFd(assetPath);
            mp.setDataSource(currentAfd.getFileDescriptor(),
                    currentAfd.getStartOffset(), currentAfd.getLength());
            mp.setLooping(looping);
            prepareAndStart(mp);
        } catch (Exception e) {
            closeAfd();
            handlePlayError(e);
        }
    }

    /**
     * 播放 raw 资源。
     */
    public void playRaw(@RawRes int rawResId, boolean looping) {
        if (appContext == null) return;
        Uri uri = Uri.parse("android.resource://" + appContext.getPackageName() + "/" + rawResId);
        playUri(uri, looping);
    }

    public void pause() {
        MediaPlayer mp = mediaPlayer;
        if (mp == null || state != State.PLAYING) return;
        try {
            if (mp.isPlaying()) mp.pause();
        } catch (IllegalStateException ignore) {}
        state = State.PAUSED;
        notifyState();
        Listener l = listener;
        if (l != null) l.onPause();
    }

    public void resume() {
        MediaPlayer mp = mediaPlayer;
        if (mp == null || state != State.PAUSED) return;
        try {
            requestAudioFocus();
            mp.start();
            state = State.PLAYING;
            notifyState();
            startProgress();
            Listener l = listener;
            if (l != null) l.onResume();
        } catch (Exception e) {
            handlePlayError(e);
        }
    }

    public void stop() {
        stopInternal();
        Listener l = listener;
        if (l != null) l.onStop();
    }

    public void release() {
        stopProgress();
        abandonAudioFocus();
        closeAfd();
        MediaPlayer mp = mediaPlayer;
        mediaPlayer = null;
        if (mp != null) {
            try { mp.reset(); } catch (Exception ignore) {}
            try { mp.release(); } catch (Exception ignore) {}
        }
        state = State.RELEASED;
        notifyState();
    }

    public boolean isPlaying() {
        MediaPlayer mp = mediaPlayer;
        if (mp == null) return false;
        try { return mp.isPlaying(); } catch (Exception e) { return false; }
    }

    @NonNull
    public State getState() { return state; }

    public long getCurrentPositionMs() {
        MediaPlayer mp = mediaPlayer;
        if (mp == null) return 0;
        try { return mp.getCurrentPosition(); } catch (Exception e) { return 0; }
    }

    public long getDurationMs() {
        MediaPlayer mp = mediaPlayer;
        if (mp == null) return -1;
        try { return mp.getDuration(); } catch (Exception e) { return -1; }
    }

    public void seekTo(long positionMs) {
        MediaPlayer mp = mediaPlayer;
        if (mp == null) return;
        try { mp.seekTo((int) Math.max(0, positionMs)); } catch (IllegalStateException ignore) {}
    }

    /**
     * 设置播放音量 (0.0 ~ 1.0)。
     */
    public void setVolume(float volume) {
        this.volume = Math.max(0f, Math.min(1f, volume));
        applyVolume();
    }

    /**
     * 获取当前 MediaPlayer 的音频 Session ID（用于绑定 Equalizer）。
     *
     * @return session ID，未就绪时返回 0
     */
    public int getAudioSessionId() {
        MediaPlayer mp = mediaPlayer;
        if (mp == null) return 0;
        try { return mp.getAudioSessionId(); } catch (Exception e) { return 0; }
    }

    // ======================== Internal ========================

    private void createOrResetPlayer() {
        if (mediaPlayer == null) {
            mediaPlayer = new MediaPlayer();
        } else {
            try {
                mediaPlayer.reset();
            } catch (IllegalStateException e) {
                try { mediaPlayer.release(); } catch (Exception ignore) {}
                mediaPlayer = new MediaPlayer();
            }
        }
        MediaPlayer mp = mediaPlayer;
        mp.setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && preferredDevice != null) {
            mp.setPreferredDevice(preferredDevice);
        }
        applyVolume();
    }

    private void prepareAndStart(@NonNull MediaPlayer mp) {
        state = State.PREPARING;
        notifyState();
        mp.setOnPreparedListener(p -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && preferredDevice != null) {
                p.setPreferredDevice(preferredDevice);
            }
            applyVolume();
            try {
                requestAudioFocus();
                p.start();
                state = State.PLAYING;
                notifyState();
                startProgress();
                Listener l = listener;
                if (l != null) l.onStart();
            } catch (IllegalStateException e) {
                handlePlayError(e);
            }
        });
        mp.setOnCompletionListener(p -> {
            closeAfd();
            state = State.STOPPED;
            notifyState();
            stopProgress();
            abandonAudioFocus();
            Listener l = listener;
            if (l != null) l.onComplete();
        });
        mp.setOnErrorListener((p, what, extra) -> {
            closeAfd();
            state = State.STOPPED;
            notifyState();
            stopProgress();
            abandonAudioFocus();
            Listener l = listener;
            if (l != null) l.onError(what, extra, null);
            return true;
        });
        mp.prepareAsync();
    }

    private void stopInternal() {
        stopProgress();
        abandonAudioFocus();
        closeAfd();
        MediaPlayer mp = mediaPlayer;
        if (mp != null) {
            try { mp.stop(); } catch (Exception ignore) {}
            try { mp.reset(); } catch (Exception ignore) {}
        }
        pausedByFocusLoss = false;
        duckedByFocusLoss = false;
        state = State.STOPPED;
        notifyState();
    }

    private void applyVolume() {
        MediaPlayer mp = mediaPlayer;
        if (mp == null) return;
        float v = duckedByFocusLoss ? volume * DUCK_VOLUME_FACTOR : volume;
        try { mp.setVolume(v, v); } catch (IllegalStateException ignore) {}
    }

    private void closeAfd() {
        AssetFileDescriptor afd = currentAfd;
        currentAfd = null;
        if (afd != null) {
            try { afd.close(); } catch (IOException ignore) {}
        }
    }

    private void requestAudioFocus() {
        AudioManager am = audioManager;
        if (am == null || hasAudioFocus) return;
        int result;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (audioFocusRequest == null) {
                AudioAttributes attrs = new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build();
                audioFocusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                        .setAudioAttributes(attrs)
                        .setOnAudioFocusChangeListener(focusChangeListener)
                        .build();
            }
            result = am.requestAudioFocus(audioFocusRequest);
        } else {
            result = am.requestAudioFocus(focusChangeListener,
                    AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
        }
        hasAudioFocus = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
    }

    private void abandonAudioFocus() {
        AudioManager am = audioManager;
        if (am == null || !hasAudioFocus) {
            hasAudioFocus = false;
            return;
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && audioFocusRequest != null) {
                am.abandonAudioFocusRequest(audioFocusRequest);
            } else {
                am.abandonAudioFocus(focusChangeListener);
            }
        } catch (Exception ignore) {}
        hasAudioFocus = false;
        pausedByFocusLoss = false;
        duckedByFocusLoss = false;
    }

    private void handleFocusGain() {
        boolean shouldResume = pausedByFocusLoss && state == State.PAUSED;
        pausedByFocusLoss = false;
        if (duckedByFocusLoss) {
            duckedByFocusLoss = false;
            applyVolume();
        }
        if (shouldResume) resume();
    }

    private void handleTransientLoss() {
        if (state == State.PLAYING) {
            pausedByFocusLoss = true;
            pause();
        }
    }

    private void handleDuck() {
        duckedByFocusLoss = true;
        applyVolume();
    }

    private void handlePlayError(@NonNull Exception e) {
        Log.e(TAG, "play error", e);
        closeAfd();
        state = State.STOPPED;
        notifyState();
        stopProgress();
        abandonAudioFocus();
        Listener l = listener;
        if (l != null) l.onError(-1, -1, e);
    }

    private void notifyState() {
        Listener l = listener;
        if (l != null) l.onStateChanged(state);
    }

    private void startProgress() {
        mainHandler.removeCallbacks(progressRunnable);
        mainHandler.postDelayed(progressRunnable, progressIntervalMs);
    }

    private void stopProgress() {
        mainHandler.removeCallbacks(progressRunnable);
    }
}
