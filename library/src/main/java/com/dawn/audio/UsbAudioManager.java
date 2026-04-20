package com.dawn.audio;

import android.content.Context;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * USB 扬声器管理器 — 检测、路由和管理 USB 音频输出设备。
 * <p>
 * 功能：
 * <ul>
 *   <li>自动检测 USB 音频设备连接/断开</li>
 *   <li>查找并返回 USB 音频输出设备</li>
 *   <li>设备信息描述</li>
 *   <li>热插拔回调通知</li>
 * </ul>
 * <p>
 * 使用示例：
 * <pre>
 *   UsbAudioManager usbMgr = new UsbAudioManager();
 *   usbMgr.init(context);
 *   usbMgr.setDeviceChangeListener(device -> {
 *       if (device != null) {
 *           musicPlayer.setPreferredDevice(device);
 *       }
 *   });
 *   AudioDeviceInfo usb = usbMgr.findUsbAudioOutput();
 *   // ...
 *   usbMgr.release();
 * </pre>
 */
public class UsbAudioManager {

    private static final String TAG = "UsbAudioManager";

    /**
     * USB 音频设备变化监听器。
     */
    public interface DeviceChangeListener {
        /**
         * USB 音频设备发生变化。
         *
         * @param usbDevice 当前可用的 USB 音频输出设备，为 null 表示没有 USB 音频设备
         */
        void onUsbAudioDeviceChanged(@Nullable AudioDeviceInfo usbDevice);
    }

    @Nullable
    private AudioManager audioManager;
    @Nullable
    private DeviceChangeListener deviceChangeListener;
    @Nullable
    private AudioDeviceInfo currentUsbDevice;
    private boolean callbackRegistered;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private final AudioDeviceCallback audioDeviceCallback = new AudioDeviceCallback() {
        @Override
        public void onAudioDevicesAdded(AudioDeviceInfo[] addedDevices) {
            refreshUsbDevice();
        }

        @Override
        public void onAudioDevicesRemoved(AudioDeviceInfo[] removedDevices) {
            refreshUsbDevice();
        }
    };

    /**
     * 初始化，开始监听 USB 音频设备。
     */
    public void init(@NonNull Context context) {
        audioManager = (AudioManager) context.getApplicationContext()
                .getSystemService(Context.AUDIO_SERVICE);
        registerCallback();
        refreshUsbDevice();
    }

    public void setDeviceChangeListener(@Nullable DeviceChangeListener listener) {
        this.deviceChangeListener = listener;
    }

    /**
     * 查找当前连接的 USB 音频输出设备。
     *
     * @return USB 音频设备，未找到返回 null
     */
    @Nullable
    public AudioDeviceInfo findUsbAudioOutput() {
        AudioManager am = audioManager;
        if (am == null) return null;
        AudioDeviceInfo[] devices = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS);
        if (devices == null) return null;
        for (AudioDeviceInfo device : devices) {
            if (device != null && device.isSink() && isUsbAudio(device)) {
                return device;
            }
        }
        return null;
    }

    /**
     * 查找首选输出设备：优先 USB，其次蓝牙/有线耳机，最后系统默认。
     */
    @Nullable
    public AudioDeviceInfo findPreferredOutput() {
        AudioManager am = audioManager;
        if (am == null) return null;
        AudioDeviceInfo[] devices = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS);
        if (devices == null) return null;
        AudioDeviceInfo fallback = null;
        for (AudioDeviceInfo device : devices) {
            if (device == null || !device.isSink()) continue;
            if (isUsbAudio(device)) {
                return device;
            }
            if (fallback == null && isPreferredNonUsb(device)) {
                fallback = device;
            }
        }
        return fallback;
    }

    /**
     * 是否有 USB 音频设备连接。
     */
    public boolean isUsbAudioConnected() {
        return findUsbAudioOutput() != null;
    }

    /**
     * 获取缓存的当前 USB 音频设备。
     */
    @Nullable
    public AudioDeviceInfo getCurrentUsbDevice() {
        return currentUsbDevice;
    }

    /**
     * 列出所有音频输出设备信息。
     *
     * @return 可读的设备列表字符串
     */
    @NonNull
    public String listAllOutputDevices() {
        AudioManager am = audioManager;
        if (am == null) return "AudioManager not initialized";
        AudioDeviceInfo[] devices = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS);
        if (devices == null || devices.length == 0) return "No output devices";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < devices.length; i++) {
            if (i > 0) sb.append("\n");
            sb.append(i + 1).append(". ").append(describeDevice(devices[i]));
        }
        return sb.toString();
    }

    /**
     * 描述设备信息。
     */
    @NonNull
    public static String describeDevice(@Nullable AudioDeviceInfo device) {
        if (device == null) return "null";
        CharSequence name = device.getProductName();
        String productName = name != null ? name.toString() : "unknown";
        return productName + " (" + getTypeLabel(device.getType()) + "), id=" + device.getId()
                + ", sink=" + device.isSink();
    }

    /**
     * 释放资源，取消设备监听。
     */
    public void release() {
        unregisterCallback();
        currentUsbDevice = null;
        deviceChangeListener = null;
        audioManager = null;
    }

    // ======================== Internal ========================

    private void registerCallback() {
        AudioManager am = audioManager;
        if (am == null || callbackRegistered) return;
        try {
            am.registerAudioDeviceCallback(audioDeviceCallback, mainHandler);
            callbackRegistered = true;
        } catch (Exception e) {
            Log.e(TAG, "register callback failed", e);
        }
    }

    private void unregisterCallback() {
        AudioManager am = audioManager;
        if (am == null || !callbackRegistered) {
            callbackRegistered = false;
            return;
        }
        try {
            am.unregisterAudioDeviceCallback(audioDeviceCallback);
        } catch (Exception e) {
            Log.e(TAG, "unregister callback failed", e);
        } finally {
            callbackRegistered = false;
        }
    }

    private void refreshUsbDevice() {
        AudioDeviceInfo newUsb = findUsbAudioOutput();
        boolean changed;
        if (currentUsbDevice == null && newUsb == null) {
            changed = false;
        } else if (currentUsbDevice == null || newUsb == null) {
            changed = true;
        } else {
            changed = currentUsbDevice.getId() != newUsb.getId();
        }
        currentUsbDevice = newUsb;
        if (changed) {
            Log.i(TAG, "USB audio device changed: " +
                    (newUsb != null ? describeDevice(newUsb) : "disconnected"));
            DeviceChangeListener l = deviceChangeListener;
            if (l != null) {
                l.onUsbAudioDeviceChanged(newUsb);
            }
        }
    }

    private static boolean isUsbAudio(@NonNull AudioDeviceInfo device) {
        int type = device.getType();
        return type == AudioDeviceInfo.TYPE_USB_DEVICE
                || type == AudioDeviceInfo.TYPE_USB_HEADSET
                || type == AudioDeviceInfo.TYPE_DOCK;
    }

    private static boolean isPreferredNonUsb(@NonNull AudioDeviceInfo device) {
        int type = device.getType();
        return type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES
                || type == AudioDeviceInfo.TYPE_WIRED_HEADSET
                || type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
                || type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER;
    }

    @NonNull
    private static String getTypeLabel(int type) {
        switch (type) {
            case AudioDeviceInfo.TYPE_BUILTIN_SPEAKER: return "Built-in Speaker";
            case AudioDeviceInfo.TYPE_WIRED_HEADPHONES: return "Wired Headphones";
            case AudioDeviceInfo.TYPE_WIRED_HEADSET: return "Wired Headset";
            case AudioDeviceInfo.TYPE_BLUETOOTH_A2DP: return "Bluetooth A2DP";
            case AudioDeviceInfo.TYPE_USB_DEVICE: return "USB Audio";
            case AudioDeviceInfo.TYPE_USB_HEADSET: return "USB Headset";
            case AudioDeviceInfo.TYPE_DOCK: return "Dock";
            case AudioDeviceInfo.TYPE_HDMI: return "HDMI";
            default: return "type=" + type;
        }
    }
}
