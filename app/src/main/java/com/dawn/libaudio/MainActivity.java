package com.dawn.libaudio;

import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.dawn.audio.EqualizerManager;
import com.dawn.audio.MusicPlayer;
import com.dawn.audio.SoundEffectPlayer;
import com.dawn.audio.UsbAudioManager;
import com.dawn.audio.VolumeManager;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "LibAudioDemo";

    private VolumeManager volumeManager;
    private SoundEffectPlayer soundEffectPlayer;
    private MusicPlayer musicPlayer;
    private UsbAudioManager usbAudioManager;
    private EqualizerManager equalizerManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // ============ 1. VolumeManager ============
        volumeManager = new VolumeManager();
        volumeManager.init(this);

        int musicVol = volumeManager.getVolume(AudioManager.STREAM_MUSIC);
        int maxVol = volumeManager.getMaxVolume(AudioManager.STREAM_MUSIC);
        int percent = volumeManager.getVolumePercent(AudioManager.STREAM_MUSIC);
        Log.i(TAG, "Music volume: " + musicVol + "/" + maxVol + " (" + percent + "%)");

        // Set volume to 80%
        volumeManager.setVolumePercent(AudioManager.STREAM_MUSIC, 80, false);
        Log.i(TAG, "Volume set to 80%");

        // ============ 2. SoundEffectPlayer ============
        soundEffectPlayer = new SoundEffectPlayer();
        soundEffectPlayer.init(this, 4);
        // Load sounds: soundEffectPlayer.loadFromResource("click", R.raw.click);
        // Play: soundEffectPlayer.play("click");
        Log.i(TAG, "SoundEffectPlayer initialized");

        // ============ 3. MusicPlayer ============
        musicPlayer = new MusicPlayer();
        musicPlayer.init(this);
        musicPlayer.setListener(new MusicPlayer.Listener() {
            @Override
            public void onStart() {
                Log.i(TAG, "Music started");
            }

            @Override
            public void onComplete() {
                Log.i(TAG, "Music completed");
            }

            @Override
            public void onProgress(long positionMs, long durationMs) {
                Log.d(TAG, "Progress: " + positionMs + "/" + durationMs);
            }

            @Override
            public void onError(int what, int extra, Exception exception) {
                Log.e(TAG, "Music error: what=" + what + ", extra=" + extra, exception);
            }
        });
        // Play: musicPlayer.playFromAssets("music/bgm.mp3", true);
        // Play: musicPlayer.play("/sdcard/Music/song.mp3", false);
        Log.i(TAG, "MusicPlayer initialized");

        // ============ 4. UsbAudioManager ============
        usbAudioManager = new UsbAudioManager();
        usbAudioManager.init(this);
        usbAudioManager.setDeviceChangeListener(usbDevice -> {
            if (usbDevice != null) {
                Log.i(TAG, "USB audio connected: " + UsbAudioManager.describeDevice(usbDevice));
                musicPlayer.setPreferredDevice(usbDevice);
                Toast.makeText(this, "USB speaker connected", Toast.LENGTH_SHORT).show();
            } else {
                Log.i(TAG, "USB audio disconnected");
                musicPlayer.setPreferredDevice(null);
            }
        });

        AudioDeviceInfo usbDevice = usbAudioManager.findUsbAudioOutput();
        if (usbDevice != null) {
            musicPlayer.setPreferredDevice(usbDevice);
            Log.i(TAG, "USB audio output: " + UsbAudioManager.describeDevice(usbDevice));
        }
        Log.i(TAG, "All output devices:\n" + usbAudioManager.listAllOutputDevices());

        // ============ 5. EqualizerManager ============
        equalizerManager = new EqualizerManager();
        int sessionId = musicPlayer.getAudioSessionId();
        if (sessionId > 0) {
            equalizerManager.init(sessionId);
            equalizerManager.applyPreset(EqualizerManager.PRESET_BASS);
            Log.i(TAG, "EQ preset: " + equalizerManager.getCurrentPreset());
            Log.i(TAG, "EQ bands: " + equalizerManager.encodeBandLevels());
        }

        Log.i(TAG, "=== lib-audio demo ready ===");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (equalizerManager != null) equalizerManager.release();
        if (musicPlayer != null) musicPlayer.release();
        if (soundEffectPlayer != null) soundEffectPlayer.release();
        if (usbAudioManager != null) usbAudioManager.release();
    }
}
