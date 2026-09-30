package net.sourceforge.opencamera;

import android.util.Log;
import java.nio.ByteBuffer;

/**
 * OMTSender - Java wrapper for the Open Media Transport sender.
 * 
 * This class provides a high-level interface to stream video frames
 * from the Android camera to OMT receivers on the local network.
 * 
 * Usage:
 * 1. Create an instance of OMTSender
 * 2. Call init() with your desired configuration
 * 3. Call sendFrame() for each camera frame
 * 4. Call cleanup() when done streaming
 */
public class OMTSender {

    private static final String TAG = "OMTSender";

    /**
     * Quality/Compression constants for OMT video codec (VMX 4:2:2)
     * 
     * Bandwidth reference at 1080p30:
     * - LOW: ~43 Mbps (Best for Wi-Fi stability)
     * - MEDIUM: ~100 Mbps (Requires good Wi-Fi 5/6)
     * - HIGH: ~130 Mbps (Requires Wi-Fi 6 or wired connection)
     */
    public static final int QUALITY_DEFAULT = 0; // Auto
    public static final int QUALITY_LOW = 1; // ~43 Mbps @ 1080p30 (Recommended for Wi-Fi)
    public static final int QUALITY_MEDIUM = 50; // ~100 Mbps @ 1080p30
    public static final int QUALITY_HIGH = 100; // ~130 Mbps @ 1080p30 (Wired/Wi-Fi 6 only)

    private static boolean isNativeLoaded = false;
    private boolean isInitialized = false;

    public static boolean isNativeAvailable() {
        return isNativeLoaded;
    }

    public static boolean isNativeLoaded() {
        return isNativeLoaded;
    }

    // Static block to load native libraries
    static {
        try {
            // Load the VMX codec library first (dependency)
            System.loadLibrary("vmx");
            Log.i(TAG, "Loaded libvmx.so");

            // Load the OMT library
            System.loadLibrary("omt");
            Log.i(TAG, "Loaded libomt.so");

            // Load our OMT bridge library with the JNI functions
            System.loadLibrary("omtbridge");
            Log.i(TAG, "Loaded libomtbridge.so");
            isNativeLoaded = true;
        } catch (Throwable e) {
            Log.w(TAG, "Failed to load native libraries (OMT streaming will be disabled): " + e.getMessage());
            isNativeLoaded = false;
        }
    }

    /**
     * Initialize the OMT sender.
     * 
     * @param name      The name of the sender (visible in discovery)
     * @param width     Video width in pixels
     * @param height    Video height in pixels
     * @param frameRate Frame rate
     * @param quality   Video quality (0=Default, 1=Low, 50=Medium, 100=High)
     * @return true if initialization succeeded
     */
    public boolean init(String name, int width, int height, int frameRate, int quality) {
        if (!isNativeLoaded) {
            Log.w(TAG, "Cannot init OMTSender: native libraries not loaded");
            return false;
        }

        if (isInitialized) {
            Log.w(TAG, "OMTSender already initialized, cleaning up first");
            cleanup();
        }

        try {
            isInitialized = nativeInit(name, width, height, frameRate, 1, quality);
        } catch (Throwable t) {
            Log.e(TAG, "Exception calling nativeInit", t);
            isInitialized = false;
        }

        if (isInitialized) {
            Log.i(TAG, "OMT Sender initialized: " + name + " (" + width + "x" + height + " @ " + frameRate
                    + "fps, Quality: " + quality + ")");
        } else {
            Log.e(TAG, "Failed to initialize OMT Sender");
        }

        return isInitialized;
    }

    /**
     * Set additional sender information (e.g. device model).
     * Must be called after init().
     */
    public void setSenderInfo(String productName, String manufacturer) {
        if (isNativeLoaded && isInitialized) {
            try {
                nativeSetSenderInfo(productName, manufacturer);
            } catch (Throwable t) {
                Log.e(TAG, "Exception calling nativeSetSenderInfo", t);
            }
        }
    }

    /**
     * Initialize with default quality (MEDIUM).
     */
    public boolean init(String name, int width, int height, int frameRate) {
        return init(name, width, height, frameRate, QUALITY_MEDIUM);
    }

    /**
     * Send a video frame to connected receivers.
     * 
     * The buffer should contain NV12 formatted pixel data (Y plane followed by UV
     * plane).
     * This is the native format from Android's Camera2 API with
     * ImageFormat.YUV_420_888.
     * 
     * @param buffer   Direct ByteBuffer containing NV12 pixel data
     * @param width    Frame width
     * @param height   Frame height
     * @param yStride  Stride of the Y plane (may include row padding)
     * @param uvStride Stride of the UV plane
     * @return true if frame was sent successfully
     */
    public boolean sendFrame(ByteBuffer buffer, int width, int height, int yStride, int uvStride) {
        if (!isNativeLoaded || !isInitialized) {
            Log.e(TAG, "Cannot send frame: OMTSender not initialized or native library unavailable");
            return false;
        }

        if (!buffer.isDirect()) {
            Log.e(TAG, "Buffer must be a direct ByteBuffer");
            return false;
        }

        try {
            return nativeSendFrame(buffer, width, height, yStride, uvStride);
        } catch (Throwable t) {
            Log.e(TAG, "Exception calling nativeSendFrame", t);
            return false;
        }
    }

    /**
     * Get the number of currently connected receivers.
     */
    public int getConnectionCount() {
        if (!isNativeLoaded || !isInitialized) return 0;
        try {
            return nativeGetConnectionCount();
        } catch (Throwable t) {
            return 0;
        }
    }

    /**
     * Get the discovery address for this sender.
     * Format: "HOSTNAME (Name)"
     */
    public String getAddress() {
        if (!isNativeLoaded || !isInitialized) return null;
        try {
            return nativeGetAddress();
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Check if OMT sender is currently initialized and ready.
     */
    public boolean isReady() {
        return isNativeLoaded && isInitialized;
    }

    /**
     * Cleanup and release resources.
     * Call this when streaming is stopped.
     */
    public void cleanup() {
        if (isNativeLoaded && isInitialized) {
            try {
                nativeCleanup();
            } catch (Throwable t) {
                Log.e(TAG, "Exception during nativeCleanup", t);
            }
            isInitialized = false;
            Log.i(TAG, "OMT Sender cleaned up");
        } else {
            isInitialized = false;
        }
    }

    /**
     * Set the current VMX quality level on the fly.
     * 
     * @param quality Video quality (0=Default, 1=Low, 50=Medium, 100=High)
     */
    public void setQuality(int quality) {
        if (isNativeLoaded && isInitialized) {
            try {
                nativeSetQuality(quality);
            } catch (Throwable t) {
                Log.e(TAG, "Exception in nativeSetQuality", t);
            }
        }
    }

    // =============================================================
    // Statistics API (for monitoring and UI feedback)
    // =============================================================

    /**
     * Get total frames sent since initialization.
     */
    public long getFramesSent() {
        if (!isNativeLoaded || !isInitialized) return 0;
        try {
            return nativeGetFramesSent();
        } catch (Throwable t) {
            return 0;
        }
    }

    /**
     * Get total frames dropped since initialization.
     * Frames are dropped when the receiver can't keep up (network congestion).
     */
    public long getFramesDropped() {
        if (!isNativeLoaded || !isInitialized) return 0;
        try {
            return nativeGetFramesDropped();
        } catch (Throwable t) {
            return 0;
        }
    }

    /**
     * Get recent frame drops and reset the counter.
     * Use this for periodic UI updates - call every second to get drops/second.
     * 
     * @return Number of frames dropped since last call
     */
    public long getRecentDropsAndReset() {
        if (!isNativeLoaded || !isInitialized) return 0;
        try {
            return nativeGetRecentDropsAndReset();
        } catch (Throwable t) {
            return 0;
        }
    }

    /**
     * Get total bytes sent since initialization.
     */
    public long getBytesSent() {
        if (!isNativeLoaded || !isInitialized) return 0;
        try {
            return nativeGetBytesSent();
        } catch (Throwable t) {
            return 0;
        }
    }

    /**
     * Get drop rate as percentage (0-100).
     * 
     * @return Drop rate percentage, or 0 if no frames sent
     */
    public float getDropRatePercent() {
        long sent = getFramesSent();
        long dropped = getFramesDropped();
        if (sent == 0)
            return 0f;
        return (dropped * 100f) / sent;
    }

    // Native method declarations
    private native boolean nativeInit(String name, int width, int height, int frameRateN, int frameRateD, int quality);

    private native boolean nativeSendFrame(ByteBuffer buffer, int width, int height, int yStride, int uvStride);

    private native int nativeGetConnectionCount();

    private native String nativeGetAddress();

    private native void nativeSetSenderInfo(String productName, String manufacturer);

    private native void nativeCleanup();

    // Statistics native methods
    private native long nativeGetFramesSent();

    private native long nativeGetFramesDropped();

    private native long nativeGetRecentDropsAndReset();

    private native long nativeGetBytesSent();

    private native void nativeSetQuality(int quality);

    private native int nativeGetProfile();

    /**
     * Get the current VMX profile ID.
     */
    public int getProfile() {
        if (!isNativeLoaded || !isInitialized) return 0;
        try {
            return nativeGetProfile();
        } catch (Throwable t) {
            return 0;
        }
    }
}
