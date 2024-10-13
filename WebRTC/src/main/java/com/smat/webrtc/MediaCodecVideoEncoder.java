package com.smat.webrtc;

import android.annotation.TargetApi;
import android.graphics.Matrix;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaCrypto;
import android.media.MediaFormat;
import android.opengl.GLES20;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.view.Surface;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.webrtc.EglBase;
import org.webrtc.EglBase14;
import org.webrtc.VideoFrame;
@Deprecated
@TargetApi(19)
/* loaded from: input.aar:classes.jar:org/webrtc/MediaCodecVideoEncoder.class */
public class MediaCodecVideoEncoder {
    private static final String TAG = "MediaCodecVideoEncoder";
    private static final int MEDIA_CODEC_RELEASE_TIMEOUT_MS = 5000;
    private static final int DEQUEUE_TIMEOUT = 0;
    private static final int BITRATE_ADJUSTMENT_FPS = 30;
    private static final int MAXIMUM_INITIAL_FPS = 30;
    private static final double BITRATE_CORRECTION_SEC = 3.0d;
    private static final double BITRATE_CORRECTION_MAX_SCALE = 4.0d;
    private static final int BITRATE_CORRECTION_STEPS = 20;
    private static final long QCOM_VP8_KEY_FRAME_INTERVAL_ANDROID_L_MS = 15000;
    private static final long QCOM_VP8_KEY_FRAME_INTERVAL_ANDROID_M_MS = 20000;
    private static final long QCOM_VP8_KEY_FRAME_INTERVAL_ANDROID_N_MS = 15000;
    @Nullable
    private static MediaCodecVideoEncoder runningInstance;
    @Nullable
    private static MediaCodecVideoEncoderErrorCallback errorCallback;
    private static int codecErrors;
    @Nullable
    private static EglBase staticEglBase;
    @Nullable
    private Thread mediaCodecThread;
    @Nullable
    private MediaCodec mediaCodec;
    private ByteBuffer[] outputBuffers;
    @Nullable
    private EglBase14 eglBase;
    private int profile;
    private int width;
    private int height;
    @Nullable
    private Surface inputSurface;
    @Nullable
    private GlRectDrawer drawer;
    private static final String VP8_MIME_TYPE = "video/x-vnd.on2.vp8";
    private static final String VP9_MIME_TYPE = "video/x-vnd.on2.vp9";
    private static final String H264_MIME_TYPE = "video/avc";
    private static final int VIDEO_AVCProfileHigh = 8;
    private static final int VIDEO_AVCLevel3 = 256;
    private static final int VIDEO_ControlRateConstant = 2;
    private VideoCodecType type;
    private int colorFormat;
    private BitrateAdjustmentType bitrateAdjustmentType = BitrateAdjustmentType.NO_ADJUSTMENT;
    private double bitrateAccumulator;
    private double bitrateAccumulatorMax;
    private double bitrateObservationTimeMs;
    private int bitrateAdjustmentScaleExp;
    private int targetBitrateBps;
    private int targetFps;
    private long forcedKeyFrameMs;
    private long lastKeyFrameMs;
    @Nullable
    private ByteBuffer configData;
    private static Set<String> hwEncoderDisabledTypes = new HashSet();
    private static final MediaCodecProperties qcomVp8HwProperties = new MediaCodecProperties("OMX.qcom.", 19, BitrateAdjustmentType.NO_ADJUSTMENT);
    private static final MediaCodecProperties exynosVp8HwProperties = new MediaCodecProperties("OMX.Exynos.", 23, BitrateAdjustmentType.DYNAMIC_ADJUSTMENT);
    private static final MediaCodecProperties intelVp8HwProperties = new MediaCodecProperties("OMX.Intel.", 21, BitrateAdjustmentType.NO_ADJUSTMENT);
    private static final MediaCodecProperties qcomVp9HwProperties = new MediaCodecProperties("OMX.qcom.", 24, BitrateAdjustmentType.NO_ADJUSTMENT);
    private static final MediaCodecProperties exynosVp9HwProperties = new MediaCodecProperties("OMX.Exynos.", 24, BitrateAdjustmentType.FRAMERATE_ADJUSTMENT);
    private static final MediaCodecProperties[] vp9HwList = {qcomVp9HwProperties, exynosVp9HwProperties};
    private static final MediaCodecProperties qcomH264HwProperties = new MediaCodecProperties("OMX.qcom.", 19, BitrateAdjustmentType.NO_ADJUSTMENT);
    private static final MediaCodecProperties exynosH264HwProperties = new MediaCodecProperties("OMX.Exynos.", 21, BitrateAdjustmentType.FRAMERATE_ADJUSTMENT);
    private static final MediaCodecProperties mediatekH264HwProperties = new MediaCodecProperties("OMX.MTK.", 27, BitrateAdjustmentType.FRAMERATE_ADJUSTMENT);
    private static final MediaCodecProperties exynosH264HighProfileHwProperties = new MediaCodecProperties("OMX.Exynos.", 23, BitrateAdjustmentType.FRAMERATE_ADJUSTMENT);
    private static final MediaCodecProperties[] h264HighProfileHwList = {exynosH264HighProfileHwProperties};
    private static final String[] H264_HW_EXCEPTION_MODELS = {"SAMSUNG-SGH-I337", "Nexus 7", "Nexus 4"};
    private static final int COLOR_QCOM_FORMATYUV420PackedSemiPlanar32m = 2141391876;
    private static final int[] supportedColorList = {19, 21, 2141391872, COLOR_QCOM_FORMATYUV420PackedSemiPlanar32m};
    private static final int[] supportedSurfaceColorList = {2130708361};

    /* loaded from: input.aar:classes.jar:org/webrtc/MediaCodecVideoEncoder$BitrateAdjustmentType.class */
    public enum BitrateAdjustmentType {
        NO_ADJUSTMENT,
        FRAMERATE_ADJUSTMENT,
        DYNAMIC_ADJUSTMENT
    }

    /* loaded from: input.aar:classes.jar:org/webrtc/MediaCodecVideoEncoder$MediaCodecVideoEncoderErrorCallback.class */
    public interface MediaCodecVideoEncoderErrorCallback {
        void onMediaCodecVideoEncoderCriticalError(int i);
    }

    private static native void nativeFillInputBuffer(long j, int i, ByteBuffer byteBuffer, int i2, ByteBuffer byteBuffer2, int i3, ByteBuffer byteBuffer3, int i4);

    /* JADX INFO: Access modifiers changed from: private */
    public static native long nativeCreateEncoder(VideoCodecInfo videoCodecInfo, boolean z);

    public static VideoEncoderFactory createFactory() {
        return new DefaultVideoEncoderFactory(new HwEncoderFactory());
    }

    /* loaded from: input.aar:classes.jar:org/webrtc/MediaCodecVideoEncoder$HwEncoderFactory.class */
    static class HwEncoderFactory implements VideoEncoderFactory {
        private final VideoCodecInfo[] supportedHardwareCodecs = getSupportedHardwareCodecs();

        HwEncoderFactory() {
        }

        private static boolean isSameCodec(VideoCodecInfo codecA, VideoCodecInfo codecB) {
            if (!codecA.name.equalsIgnoreCase(codecB.name)) {
                return false;
            }
            if (codecA.name.equalsIgnoreCase("H264")) {
                return H264Utils.isSameH264Profile(codecA.params, codecB.params);
            }
            return true;
        }

        private static boolean isCodecSupported(VideoCodecInfo[] supportedCodecs, VideoCodecInfo codec) {
            int length = supportedCodecs.length;
            for (int i = MediaCodecVideoEncoder.DEQUEUE_TIMEOUT; i < length; i++) {
                VideoCodecInfo supportedCodec = supportedCodecs[i];
                if (isSameCodec(supportedCodec, codec)) {
                    return true;
                }
            }
            return false;
        }

        private static VideoCodecInfo[] getSupportedHardwareCodecs() {
            List<VideoCodecInfo> codecs = new ArrayList<>();
            if (MediaCodecVideoEncoder.isVp8HwSupported()) {
                Logging.d(MediaCodecVideoEncoder.TAG, "VP8 HW Encoder supported.");
                codecs.add(new VideoCodecInfo("VP8", new HashMap()));
            }
            if (MediaCodecVideoEncoder.isVp9HwSupported()) {
                Logging.d(MediaCodecVideoEncoder.TAG, "VP9 HW Encoder supported.");
                codecs.add(new VideoCodecInfo("VP9", new HashMap()));
            }
            if (MediaCodecVideoDecoder.isH264HighProfileHwSupported()) {
                Logging.d(MediaCodecVideoEncoder.TAG, "H.264 High Profile HW Encoder supported.");
                codecs.add(H264Utils.DEFAULT_H264_HIGH_PROFILE_CODEC);
            }
            if (MediaCodecVideoEncoder.isH264HwSupported()) {
                Logging.d(MediaCodecVideoEncoder.TAG, "H.264 HW Encoder supported.");
                codecs.add(H264Utils.DEFAULT_H264_BASELINE_PROFILE_CODEC);
            }
            return (VideoCodecInfo[]) codecs.toArray(new VideoCodecInfo[codecs.size()]);
        }

        @Override // org.webrtc.VideoEncoderFactory
        public VideoCodecInfo[] getSupportedCodecs() {
            return this.supportedHardwareCodecs;
        }

        @Override // org.webrtc.VideoEncoderFactory
        @Nullable
        public VideoEncoder createEncoder(final VideoCodecInfo info) {
            if (!isCodecSupported(this.supportedHardwareCodecs, info)) {
                Logging.d(MediaCodecVideoEncoder.TAG, "No HW video encoder for codec " + info.name);
                return null;
            }
            Logging.d(MediaCodecVideoEncoder.TAG, "Create HW video encoder for " + info.name);
            return new WrappedNativeVideoEncoder() { // from class: org.webrtc.MediaCodecVideoEncoder.HwEncoderFactory.1
                @Override // org.webrtc.WrappedNativeVideoEncoder, org.webrtc.VideoEncoder
                public long createNativeVideoEncoder() {
                    return MediaCodecVideoEncoder.nativeCreateEncoder(info, MediaCodecVideoEncoder.staticEglBase instanceof EglBase14);
                }

                @Override // org.webrtc.WrappedNativeVideoEncoder, org.webrtc.VideoEncoder
                public boolean isHardwareEncoder() {
                    return true;
                }
            };
        }
    }

    /* loaded from: input.aar:classes.jar:org/webrtc/MediaCodecVideoEncoder$VideoCodecType.class */
    public enum VideoCodecType {
        VIDEO_CODEC_UNKNOWN,
        VIDEO_CODEC_VP8,
        VIDEO_CODEC_VP9,
        VIDEO_CODEC_H264;

        @CalledByNative("VideoCodecType")
        static VideoCodecType fromNativeIndex(int nativeIndex) {
            return values()[nativeIndex];
        }
    }

    /* loaded from: input.aar:classes.jar:org/webrtc/MediaCodecVideoEncoder$H264Profile.class */
    public enum H264Profile {
        CONSTRAINED_BASELINE(MediaCodecVideoEncoder.DEQUEUE_TIMEOUT),
        BASELINE(1),
        MAIN(2),
        CONSTRAINED_HIGH(3),
        HIGH(4);
        
        private final int value;

        H264Profile(int value) {
            this.value = value;
        }

        public int getValue() {
            return this.value;
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* loaded from: input.aar:classes.jar:org/webrtc/MediaCodecVideoEncoder$MediaCodecProperties.class */
    public static class MediaCodecProperties {
        public final String codecPrefix;
        public final int minSdk;
        public final BitrateAdjustmentType bitrateAdjustmentType;

        MediaCodecProperties(String codecPrefix, int minSdk, BitrateAdjustmentType bitrateAdjustmentType) {
            this.codecPrefix = codecPrefix;
            this.minSdk = minSdk;
            this.bitrateAdjustmentType = bitrateAdjustmentType;
        }
    }

    public static void setEglContext(EglBase.Context eglContext) {
        if (staticEglBase != null) {
            Logging.w(TAG, "Egl context already set.");
            staticEglBase.release();
        }
        staticEglBase = EglBase.create(eglContext);
    }

    public static void disposeEglContext() {
        if (staticEglBase != null) {
            staticEglBase.release();
            staticEglBase = null;
        }
    }

    @Nullable
    static EglBase.Context getEglContext() {
        if (staticEglBase == null) {
            return null;
        }
        return staticEglBase.getEglBaseContext();
    }

    private static MediaCodecProperties[] vp8HwList() {
        ArrayList<MediaCodecProperties> supported_codecs = new ArrayList<>();
        supported_codecs.add(qcomVp8HwProperties);
        supported_codecs.add(exynosVp8HwProperties);
        if (PeerConnectionFactory.fieldTrialsFindFullName("WebRTC-IntelVP8").equals(PeerConnectionFactory.TRIAL_ENABLED)) {
            supported_codecs.add(intelVp8HwProperties);
        }
        return (MediaCodecProperties[]) supported_codecs.toArray(new MediaCodecProperties[supported_codecs.size()]);
    }

    private static final MediaCodecProperties[] h264HwList() {
        ArrayList<MediaCodecProperties> supported_codecs = new ArrayList<>();
        supported_codecs.add(qcomH264HwProperties);
        supported_codecs.add(exynosH264HwProperties);
        if (PeerConnectionFactory.fieldTrialsFindFullName("WebRTC-MediaTekH264").equals(PeerConnectionFactory.TRIAL_ENABLED)) {
            supported_codecs.add(mediatekH264HwProperties);
        }
        return (MediaCodecProperties[]) supported_codecs.toArray(new MediaCodecProperties[supported_codecs.size()]);
    }

    public static void setErrorCallback(MediaCodecVideoEncoderErrorCallback errorCallback2) {
        Logging.d(TAG, "Set error callback");
        errorCallback = errorCallback2;
    }

    public static void disableVp8HwCodec() {
        Logging.w(TAG, "VP8 encoding is disabled by application.");
        hwEncoderDisabledTypes.add(VP8_MIME_TYPE);
    }

    public static void disableVp9HwCodec() {
        Logging.w(TAG, "VP9 encoding is disabled by application.");
        hwEncoderDisabledTypes.add(VP9_MIME_TYPE);
    }

    public static void disableH264HwCodec() {
        Logging.w(TAG, "H.264 encoding is disabled by application.");
        hwEncoderDisabledTypes.add(H264_MIME_TYPE);
    }

    public static boolean isVp8HwSupported() {
        return (hwEncoderDisabledTypes.contains(VP8_MIME_TYPE) || findHwEncoder(VP8_MIME_TYPE, vp8HwList(), supportedColorList) == null) ? false : true;
    }

    @Nullable
    public static EncoderProperties vp8HwEncoderProperties() {
        if (hwEncoderDisabledTypes.contains(VP8_MIME_TYPE)) {
            return null;
        }
        return findHwEncoder(VP8_MIME_TYPE, vp8HwList(), supportedColorList);
    }

    public static boolean isVp9HwSupported() {
        return (hwEncoderDisabledTypes.contains(VP9_MIME_TYPE) || findHwEncoder(VP9_MIME_TYPE, vp9HwList, supportedColorList) == null) ? false : true;
    }

    public static boolean isH264HwSupported() {
        return (hwEncoderDisabledTypes.contains(H264_MIME_TYPE) || findHwEncoder(H264_MIME_TYPE, h264HwList(), supportedColorList) == null) ? false : true;
    }

    public static boolean isH264HighProfileHwSupported() {
        return (hwEncoderDisabledTypes.contains(H264_MIME_TYPE) || findHwEncoder(H264_MIME_TYPE, h264HighProfileHwList, supportedColorList) == null) ? false : true;
    }

    public static boolean isVp8HwSupportedUsingTextures() {
        return (hwEncoderDisabledTypes.contains(VP8_MIME_TYPE) || findHwEncoder(VP8_MIME_TYPE, vp8HwList(), supportedSurfaceColorList) == null) ? false : true;
    }

    public static boolean isVp9HwSupportedUsingTextures() {
        return (hwEncoderDisabledTypes.contains(VP9_MIME_TYPE) || findHwEncoder(VP9_MIME_TYPE, vp9HwList, supportedSurfaceColorList) == null) ? false : true;
    }

    public static boolean isH264HwSupportedUsingTextures() {
        return (hwEncoderDisabledTypes.contains(H264_MIME_TYPE) || findHwEncoder(H264_MIME_TYPE, h264HwList(), supportedSurfaceColorList) == null) ? false : true;
    }

    /* loaded from: input.aar:classes.jar:org/webrtc/MediaCodecVideoEncoder$EncoderProperties.class */
    public static class EncoderProperties {
        public final String codecName;
        public final int colorFormat;
        public final BitrateAdjustmentType bitrateAdjustmentType;

        public EncoderProperties(String codecName, int colorFormat, BitrateAdjustmentType bitrateAdjustmentType) {
            this.codecName = codecName;
            this.colorFormat = colorFormat;
            this.bitrateAdjustmentType = bitrateAdjustmentType;
        }
    }

    @Nullable
    private static EncoderProperties findHwEncoder(String mime, MediaCodecProperties[] supportedHwCodecProperties, int[] colorList) {
        if (Build.VERSION.SDK_INT < 19) {
            return null;
        }
        if (mime.equals(H264_MIME_TYPE)) {
            List<String> exceptionModels = Arrays.asList(H264_HW_EXCEPTION_MODELS);
            if (exceptionModels.contains(Build.MODEL)) {
                Logging.w(TAG, "Model: " + Build.MODEL + " has black listed H.264 encoder.");
                return null;
            }
        }
        for (int i = DEQUEUE_TIMEOUT; i < MediaCodecList.getCodecCount(); i++) {
            MediaCodecInfo info = DEQUEUE_TIMEOUT;
            try {
                info = MediaCodecList.getCodecInfoAt(i);
            } catch (IllegalArgumentException e) {
                Logging.e(TAG, "Cannot retrieve encoder codec info", e);
            }
            if (info != null && info.isEncoder()) {
                String name = DEQUEUE_TIMEOUT;
                String[] supportedTypes = info.getSupportedTypes();
                int length = supportedTypes.length;
                int i2 = DEQUEUE_TIMEOUT;
                while (true) {
                    if (i2 >= length) {
                        break;
                    }
                    String mimeType = supportedTypes[i2];
                    if (!mimeType.equals(mime)) {
                        i2++;
                    } else {
                        name = info.getName();
                        break;
                    }
                }
                if (name == null) {
                    continue;
                } else {
                    Logging.v(TAG, "Found candidate encoder " + name);
                    boolean supportedCodec = DEQUEUE_TIMEOUT;
                    BitrateAdjustmentType bitrateAdjustmentType = BitrateAdjustmentType.NO_ADJUSTMENT;
                    int length2 = supportedHwCodecProperties.length;
                    int i3 = DEQUEUE_TIMEOUT;
                    while (true) {
                        if (i3 >= length2) {
                            break;
                        }
                        MediaCodecProperties codecProperties = supportedHwCodecProperties[i3];
                        if (name.startsWith(codecProperties.codecPrefix)) {
                            if (Build.VERSION.SDK_INT < codecProperties.minSdk) {
                                Logging.w(TAG, "Codec " + name + " is disabled due to SDK version " + Build.VERSION.SDK_INT);
                            } else {
                                if (codecProperties.bitrateAdjustmentType != BitrateAdjustmentType.NO_ADJUSTMENT) {
                                    bitrateAdjustmentType = codecProperties.bitrateAdjustmentType;
                                    Logging.w(TAG, "Codec " + name + " requires bitrate adjustment: " + bitrateAdjustmentType);
                                }
                                supportedCodec = true;
                            }
                        }
                        i3++;
                    }
                    if (supportedCodec) {
                        try {
                            MediaCodecInfo.CodecCapabilities capabilities = info.getCapabilitiesForType(mime);
                            int[] iArr = capabilities.colorFormats;
                            int length3 = iArr.length;
                            for (int i4 = DEQUEUE_TIMEOUT; i4 < length3; i4++) {
                                int colorFormat = iArr[i4];
                                Logging.v(TAG, "   Color: 0x" + Integer.toHexString(colorFormat));
                            }
                            int length4 = colorList.length;
                            for (int i5 = DEQUEUE_TIMEOUT; i5 < length4; i5++) {
                                int supportedColorFormat = colorList[i5];
                                int[] iArr2 = capabilities.colorFormats;
                                int length5 = iArr2.length;
                                for (int i6 = DEQUEUE_TIMEOUT; i6 < length5; i6++) {
                                    int codecColorFormat = iArr2[i6];
                                    if (codecColorFormat == supportedColorFormat) {
                                        Logging.d(TAG, "Found target encoder for mime " + mime + " : " + name + ". Color: 0x" + Integer.toHexString(codecColorFormat) + ". Bitrate adjustment: " + bitrateAdjustmentType);
                                        return new EncoderProperties(name, codecColorFormat, bitrateAdjustmentType);
                                    }
                                }
                            }
                            continue;
                        } catch (IllegalArgumentException e2) {
                            Logging.e(TAG, "Cannot retrieve encoder capabilities", e2);
                        }
                    } else {
                        continue;
                    }
                }
            }
        }
        return null;
    }

    @CalledByNative
    MediaCodecVideoEncoder() {
    }

    private void checkOnMediaCodecThread() {
        if (this.mediaCodecThread.getId() != Thread.currentThread().getId()) {
            throw new RuntimeException("MediaCodecVideoEncoder previously operated on " + this.mediaCodecThread + " but is now called on " + Thread.currentThread());
        }
    }

    public static void printStackTrace() {
        if (runningInstance != null && runningInstance.mediaCodecThread != null) {
            StackTraceElement[] mediaCodecStackTraces = runningInstance.mediaCodecThread.getStackTrace();
            if (mediaCodecStackTraces.length > 0) {
                Logging.d(TAG, "MediaCodecVideoEncoder stacks trace:");
                int length = mediaCodecStackTraces.length;
                for (int i = DEQUEUE_TIMEOUT; i < length; i++) {
                    StackTraceElement stackTrace = mediaCodecStackTraces[i];
                    Logging.d(TAG, stackTrace.toString());
                }
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    @Nullable
    public static MediaCodec createByCodecName(String codecName) {
        try {
            return MediaCodec.createByCodecName(codecName);
        } catch (Exception e) {
            return null;
        }
    }

    @CalledByNativeUnchecked
    boolean initEncode(VideoCodecType type, int profile, int width, int height, int kbps, int fps, boolean useSurface) {
        String mime;
        EncoderProperties properties;
        int keyFrameIntervalSec;
        int fps2;
        Logging.d(TAG, "Java initEncode: " + type + ". Profile: " + profile + " : " + width + " x " + height + ". @ " + kbps + " kbps. Fps: " + fps + ". Encode from texture : " + useSurface);
        this.profile = profile;
        this.width = width;
        this.height = height;
        if (this.mediaCodecThread != null) {
            throw new RuntimeException("Forgot to release()?");
        }
        boolean configureH264HighProfile = DEQUEUE_TIMEOUT;
        if (type == VideoCodecType.VIDEO_CODEC_VP8) {
            mime = VP8_MIME_TYPE;
            properties = findHwEncoder(VP8_MIME_TYPE, vp8HwList(), useSurface ? supportedSurfaceColorList : supportedColorList);
            keyFrameIntervalSec = 100;
        } else if (type == VideoCodecType.VIDEO_CODEC_VP9) {
            mime = VP9_MIME_TYPE;
            properties = findHwEncoder(VP9_MIME_TYPE, vp9HwList, useSurface ? supportedSurfaceColorList : supportedColorList);
            keyFrameIntervalSec = 100;
        } else if (type == VideoCodecType.VIDEO_CODEC_H264) {
            mime = H264_MIME_TYPE;
            properties = findHwEncoder(H264_MIME_TYPE, h264HwList(), useSurface ? supportedSurfaceColorList : supportedColorList);
            if (profile == H264Profile.CONSTRAINED_HIGH.getValue()) {
                EncoderProperties h264HighProfileProperties = findHwEncoder(H264_MIME_TYPE, h264HighProfileHwList, useSurface ? supportedSurfaceColorList : supportedColorList);
                if (h264HighProfileProperties != null) {
                    Logging.d(TAG, "High profile H.264 encoder supported.");
                    configureH264HighProfile = true;
                } else {
                    Logging.d(TAG, "High profile H.264 encoder requested, but not supported. Use baseline.");
                }
            }
            keyFrameIntervalSec = BITRATE_CORRECTION_STEPS;
        } else {
            throw new RuntimeException("initEncode: Non-supported codec " + type);
        }
        if (properties == null) {
            throw new RuntimeException("Can not find HW encoder for " + type);
        }
        runningInstance = this;
        this.colorFormat = properties.colorFormat;
        this.bitrateAdjustmentType = properties.bitrateAdjustmentType;
        if (this.bitrateAdjustmentType == BitrateAdjustmentType.FRAMERATE_ADJUSTMENT) {
            fps2 = 30;
        } else {
            fps2 = Math.min(fps, 30);
        }
        this.forcedKeyFrameMs = 0L;
        this.lastKeyFrameMs = -1L;
        if (type == VideoCodecType.VIDEO_CODEC_VP8 && properties.codecName.startsWith(qcomVp8HwProperties.codecPrefix)) {
            if (Build.VERSION.SDK_INT == 21 || Build.VERSION.SDK_INT == 22) {
                this.forcedKeyFrameMs = 15000L;
            } else if (Build.VERSION.SDK_INT == 23) {
                this.forcedKeyFrameMs = QCOM_VP8_KEY_FRAME_INTERVAL_ANDROID_M_MS;
            } else if (Build.VERSION.SDK_INT > 23) {
                this.forcedKeyFrameMs = 15000L;
            }
        }
        Logging.d(TAG, "Color format: " + this.colorFormat + ". Bitrate adjustment: " + this.bitrateAdjustmentType + ". Key frame interval: " + this.forcedKeyFrameMs + " . Initial fps: " + fps2);
        this.targetBitrateBps = 1000 * kbps;
        this.targetFps = fps2;
        this.bitrateAccumulatorMax = this.targetBitrateBps / 8.0d;
        this.bitrateAccumulator = 0.0d;
        this.bitrateObservationTimeMs = 0.0d;
        this.bitrateAdjustmentScaleExp = DEQUEUE_TIMEOUT;
        this.mediaCodecThread = Thread.currentThread();
        try {
            MediaFormat format = MediaFormat.createVideoFormat(mime, width, height);
            format.setInteger("bitrate", this.targetBitrateBps);
            format.setInteger("bitrate-mode", 2);
            format.setInteger("color-format", properties.colorFormat);
            format.setInteger("frame-rate", this.targetFps);
            format.setInteger("i-frame-interval", keyFrameIntervalSec);
            if (configureH264HighProfile) {
                format.setInteger("profile", VIDEO_AVCProfileHigh);
                format.setInteger("level", VIDEO_AVCLevel3);
            }
            Logging.d(TAG, "  Format: " + format);
            this.mediaCodec = createByCodecName(properties.codecName);
            this.type = type;
            if (this.mediaCodec == null) {
                Logging.e(TAG, "Can not create media encoder");
                release();
                return false;
            }
            this.mediaCodec.configure(format, (Surface) null, (MediaCrypto) null, 1);
            if (useSurface) {
                this.eglBase = EglBase.createEgl14((EglBase14.Context) getEglContext(), EglBase.CONFIG_RECORDABLE);
                this.inputSurface = this.mediaCodec.createInputSurface();
                this.eglBase.createSurface(this.inputSurface);
                this.drawer = new GlRectDrawer();
            }
            this.mediaCodec.start();
            this.outputBuffers = this.mediaCodec.getOutputBuffers();
            Logging.d(TAG, "Output buffers: " + this.outputBuffers.length);
            return true;
        } catch (IllegalStateException e) {
            Logging.e(TAG, "initEncode failed", e);
            release();
            return false;
        }
    }

    @CalledByNativeUnchecked
    ByteBuffer[] getInputBuffers() {
        ByteBuffer[] inputBuffers = this.mediaCodec.getInputBuffers();
        Logging.d(TAG, "Input buffers: " + inputBuffers.length);
        return inputBuffers;
    }

    void checkKeyFrameRequired(boolean requestedKeyFrame, long presentationTimestampUs) {
        long presentationTimestampMs = (presentationTimestampUs + 500) / 1000;
        if (this.lastKeyFrameMs < 0) {
            this.lastKeyFrameMs = presentationTimestampMs;
        }
        boolean forcedKeyFrame = DEQUEUE_TIMEOUT;
        if (!requestedKeyFrame && this.forcedKeyFrameMs > 0 && presentationTimestampMs > this.lastKeyFrameMs + this.forcedKeyFrameMs) {
            forcedKeyFrame = true;
        }
        if (requestedKeyFrame || forcedKeyFrame) {
            if (requestedKeyFrame) {
                Logging.d(TAG, "Sync frame request");
            } else {
                Logging.d(TAG, "Sync frame forced");
            }
            Bundle b = new Bundle();
            b.putInt("request-sync", DEQUEUE_TIMEOUT);
            this.mediaCodec.setParameters(b);
            this.lastKeyFrameMs = presentationTimestampMs;
        }
    }

    @CalledByNativeUnchecked
    boolean encodeBuffer(boolean isKeyframe, int inputBuffer, int size, long presentationTimestampUs) {
        checkOnMediaCodecThread();
        try {
            checkKeyFrameRequired(isKeyframe, presentationTimestampUs);
            this.mediaCodec.queueInputBuffer(inputBuffer, DEQUEUE_TIMEOUT, size, presentationTimestampUs, DEQUEUE_TIMEOUT);
            return true;
        } catch (IllegalStateException e) {
            Logging.e(TAG, "encodeBuffer failed", e);
            return false;
        }
    }

    @CalledByNativeUnchecked
    boolean encodeFrame(long nativeEncoder, boolean isKeyframe, VideoFrame frame, int bufferIndex, long presentationTimestampUs) {
        checkOnMediaCodecThread();
        try {
            checkKeyFrameRequired(isKeyframe, presentationTimestampUs);
            VideoFrame.Buffer buffer = frame.getBuffer();
            if (buffer instanceof VideoFrame.TextureBuffer) {
                VideoFrame.TextureBuffer textureBuffer = (VideoFrame.TextureBuffer) buffer;
                this.eglBase.makeCurrent();
                GLES20.glClear(16384);
                VideoFrameDrawer.drawTexture(this.drawer, textureBuffer, new Matrix(), this.width, this.height, DEQUEUE_TIMEOUT, DEQUEUE_TIMEOUT, this.width, this.height);
                this.eglBase.swapBuffers(TimeUnit.MICROSECONDS.toNanos(presentationTimestampUs));
                return true;
            }
            VideoFrame.I420Buffer i420Buffer = buffer.toI420();
            int chromaHeight = (this.height + 1) / 2;
            ByteBuffer dataY = i420Buffer.getDataY();
            ByteBuffer dataU = i420Buffer.getDataU();
            ByteBuffer dataV = i420Buffer.getDataV();
            int strideY = i420Buffer.getStrideY();
            int strideU = i420Buffer.getStrideU();
            int strideV = i420Buffer.getStrideV();
            if (dataY.capacity() < strideY * this.height) {
                throw new RuntimeException("Y-plane buffer size too small.");
            }
            if (dataU.capacity() < strideU * chromaHeight) {
                throw new RuntimeException("U-plane buffer size too small.");
            }
            if (dataV.capacity() < strideV * chromaHeight) {
                throw new RuntimeException("V-plane buffer size too small.");
            }
            nativeFillInputBuffer(nativeEncoder, bufferIndex, dataY, strideY, dataU, strideU, dataV, strideV);
            i420Buffer.release();
            int yuvSize = ((this.width * this.height) * 3) / 2;
            this.mediaCodec.queueInputBuffer(bufferIndex, DEQUEUE_TIMEOUT, yuvSize, presentationTimestampUs, DEQUEUE_TIMEOUT);
            return true;
        } catch (RuntimeException e) {
            Logging.e(TAG, "encodeFrame failed", e);
            return false;
        }
    }

    @CalledByNativeUnchecked
    void release() {
        Logging.d(TAG, "Java releaseEncoder");
        checkOnMediaCodecThread();
        final C1CaughtException caughtException = new C1CaughtException();
        boolean stopHung = DEQUEUE_TIMEOUT;
        if (this.mediaCodec != null) {
            final CountDownLatch releaseDone = new CountDownLatch(1);
            Runnable runMediaCodecRelease = new Runnable() { // from class: org.webrtc.MediaCodecVideoEncoder.1
                @Override // java.lang.Runnable
                public void run() {
                    Logging.d(MediaCodecVideoEncoder.TAG, "Java releaseEncoder on release thread");
                    try {
                        MediaCodecVideoEncoder.this.mediaCodec.stop();
                    } catch (Exception e) {
                        Logging.e(MediaCodecVideoEncoder.TAG, "Media encoder stop failed", e);
                    }
                    try {
                        MediaCodecVideoEncoder.this.mediaCodec.release();
                    } catch (Exception e2) {
                        Logging.e(MediaCodecVideoEncoder.TAG, "Media encoder release failed", e2);
                        caughtException.e = e2;
                    }
                    Logging.d(MediaCodecVideoEncoder.TAG, "Java releaseEncoder on release thread done");
                    releaseDone.countDown();
                }
            };
            new Thread(runMediaCodecRelease).start();
            if (!ThreadUtils.awaitUninterruptibly(releaseDone, 5000L)) {
                Logging.e(TAG, "Media encoder release timeout");
                stopHung = true;
            }
            this.mediaCodec = null;
        }
        this.mediaCodecThread = null;
        if (this.drawer != null) {
            this.drawer.release();
            this.drawer = null;
        }
        if (this.eglBase != null) {
            this.eglBase.release();
            this.eglBase = null;
        }
        if (this.inputSurface != null) {
            this.inputSurface.release();
            this.inputSurface = null;
        }
        runningInstance = null;
        if (stopHung) {
            codecErrors++;
            if (errorCallback != null) {
                Logging.e(TAG, "Invoke codec error callback. Errors: " + codecErrors);
                errorCallback.onMediaCodecVideoEncoderCriticalError(codecErrors);
            }
            throw new RuntimeException("Media encoder release timeout.");
        } else if (caughtException.e != null) {
            RuntimeException runtimeException = new RuntimeException(caughtException.e);
            runtimeException.setStackTrace(ThreadUtils.concatStackTraces(caughtException.e.getStackTrace(), runtimeException.getStackTrace()));
            throw runtimeException;
        } else {
            Logging.d(TAG, "Java releaseEncoder done");
        }
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    /* renamed from: org.webrtc.MediaCodecVideoEncoder$1CaughtException  reason: invalid class name */
    /* loaded from: input.aar:classes.jar:org/webrtc/MediaCodecVideoEncoder$1CaughtException.class */
    public class C1CaughtException {
        Exception e;

        C1CaughtException() {
        }
    }

    @CalledByNativeUnchecked
    private boolean setRates(int kbps, int frameRate) {
        checkOnMediaCodecThread();
        int codecBitrateBps = 1000 * kbps;
        if (this.bitrateAdjustmentType == BitrateAdjustmentType.DYNAMIC_ADJUSTMENT) {
            this.bitrateAccumulatorMax = codecBitrateBps / 8.0d;
            if (this.targetBitrateBps > 0 && codecBitrateBps < this.targetBitrateBps) {
                this.bitrateAccumulator = (this.bitrateAccumulator * codecBitrateBps) / this.targetBitrateBps;
            }
        }
        this.targetBitrateBps = codecBitrateBps;
        this.targetFps = frameRate;
        if (this.bitrateAdjustmentType == BitrateAdjustmentType.FRAMERATE_ADJUSTMENT && this.targetFps > 0) {
            codecBitrateBps = (30 * this.targetBitrateBps) / this.targetFps;
            Logging.v(TAG, "setRates: " + kbps + " -> " + (codecBitrateBps / 1000) + " kbps. Fps: " + this.targetFps);
        } else if (this.bitrateAdjustmentType == BitrateAdjustmentType.DYNAMIC_ADJUSTMENT) {
            Logging.v(TAG, "setRates: " + kbps + " kbps. Fps: " + this.targetFps + ". ExpScale: " + this.bitrateAdjustmentScaleExp);
            if (this.bitrateAdjustmentScaleExp != 0) {
                codecBitrateBps = (int) (codecBitrateBps * getBitrateScale(this.bitrateAdjustmentScaleExp));
            }
        } else {
            Logging.v(TAG, "setRates: " + kbps + " kbps. Fps: " + this.targetFps);
        }
        try {
            Bundle params = new Bundle();
            params.putInt("video-bitrate", codecBitrateBps);
            this.mediaCodec.setParameters(params);
            return true;
        } catch (IllegalStateException e) {
            Logging.e(TAG, "setRates failed", e);
            return false;
        }
    }

    @CalledByNativeUnchecked
    int dequeueInputBuffer() {
        checkOnMediaCodecThread();
        try {
            return this.mediaCodec.dequeueInputBuffer(0L);
        } catch (IllegalStateException e) {
            Logging.e(TAG, "dequeueIntputBuffer failed", e);
            return -2;
        }
    }

    /* loaded from: input.aar:classes.jar:org/webrtc/MediaCodecVideoEncoder$OutputBufferInfo.class */
    static class OutputBufferInfo {
        public final int index;
        public final ByteBuffer buffer;
        public final boolean isKeyFrame;
        public final long presentationTimestampUs;

        public OutputBufferInfo(int index, ByteBuffer buffer, boolean isKeyFrame, long presentationTimestampUs) {
            this.index = index;
            this.buffer = buffer;
            this.isKeyFrame = isKeyFrame;
            this.presentationTimestampUs = presentationTimestampUs;
        }

        @CalledByNative("OutputBufferInfo")
        int getIndex() {
            return this.index;
        }

        @CalledByNative("OutputBufferInfo")
        ByteBuffer getBuffer() {
            return this.buffer;
        }

        @CalledByNative("OutputBufferInfo")
        boolean isKeyFrame() {
            return this.isKeyFrame;
        }

        @CalledByNative("OutputBufferInfo")
        long getPresentationTimestampUs() {
            return this.presentationTimestampUs;
        }
    }

    @CalledByNativeUnchecked
    @Nullable
    OutputBufferInfo dequeueOutputBuffer() {
        checkOnMediaCodecThread();
        try {
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            int result = this.mediaCodec.dequeueOutputBuffer(info, 0L);
            if (result >= 0) {
                boolean isConfigFrame = (info.flags & 2) != 0;
                if (isConfigFrame) {
                    Logging.d(TAG, "Config frame generated. Offset: " + info.offset + ". Size: " + info.size);
                    this.configData = ByteBuffer.allocateDirect(info.size);
                    this.outputBuffers[result].position(info.offset);
                    this.outputBuffers[result].limit(info.offset + info.size);
                    this.configData.put(this.outputBuffers[result]);
                    String spsData = "";
                    int i = DEQUEUE_TIMEOUT;
                    while (true) {
                        if (i >= (info.size < VIDEO_AVCProfileHigh ? info.size : VIDEO_AVCProfileHigh)) {
                            break;
                        }
                        spsData = spsData + Integer.toHexString(this.configData.get(i) & 255) + " ";
                        i++;
                    }
                    Logging.d(TAG, spsData);
                    this.mediaCodec.releaseOutputBuffer(result, false);
                    result = this.mediaCodec.dequeueOutputBuffer(info, 0L);
                }
            }
            if (result >= 0) {
                ByteBuffer outputBuffer = this.outputBuffers[result].duplicate();
                outputBuffer.position(info.offset);
                outputBuffer.limit(info.offset + info.size);
                reportEncodedFrame(info.size);
                boolean isKeyFrame = (info.flags & 1) != 0;
                if (isKeyFrame) {
                    Logging.d(TAG, "Sync frame generated");
                }
                if (isKeyFrame && this.type == VideoCodecType.VIDEO_CODEC_H264) {
                    Logging.d(TAG, "Appending config frame of size " + this.configData.capacity() + " to output buffer with offset " + info.offset + ", size " + info.size);
                    ByteBuffer keyFrameBuffer = ByteBuffer.allocateDirect(this.configData.capacity() + info.size);
                    this.configData.rewind();
                    keyFrameBuffer.put(this.configData);
                    keyFrameBuffer.put(outputBuffer);
                    keyFrameBuffer.position(DEQUEUE_TIMEOUT);
                    return new OutputBufferInfo(result, keyFrameBuffer, isKeyFrame, info.presentationTimeUs);
                }
                return new OutputBufferInfo(result, outputBuffer.slice(), isKeyFrame, info.presentationTimeUs);
            } else if (result == -3) {
                this.outputBuffers = this.mediaCodec.getOutputBuffers();
                return dequeueOutputBuffer();
            } else if (result == -2) {
                return dequeueOutputBuffer();
            } else {
                if (result == -1) {
                    return null;
                }
                throw new RuntimeException("dequeueOutputBuffer: " + result);
            }
        } catch (IllegalStateException e) {
            Logging.e(TAG, "dequeueOutputBuffer failed", e);
            return new OutputBufferInfo(-1, null, false, -1L);
        }
    }

    private double getBitrateScale(int bitrateAdjustmentScaleExp) {
        return Math.pow(BITRATE_CORRECTION_MAX_SCALE, bitrateAdjustmentScaleExp / 20.0d);
    }

    private void reportEncodedFrame(int size) {
        if (this.targetFps == 0 || this.bitrateAdjustmentType != BitrateAdjustmentType.DYNAMIC_ADJUSTMENT) {
            return;
        }
        double expectedBytesPerFrame = this.targetBitrateBps / (8.0d * this.targetFps);
        this.bitrateAccumulator += size - expectedBytesPerFrame;
        this.bitrateObservationTimeMs += 1000.0d / this.targetFps;
        double bitrateAccumulatorCap = BITRATE_CORRECTION_SEC * this.bitrateAccumulatorMax;
        this.bitrateAccumulator = Math.min(this.bitrateAccumulator, bitrateAccumulatorCap);
        this.bitrateAccumulator = Math.max(this.bitrateAccumulator, -bitrateAccumulatorCap);
        if (this.bitrateObservationTimeMs > 3000.0d) {
            Logging.d(TAG, "Acc: " + ((int) this.bitrateAccumulator) + ". Max: " + ((int) this.bitrateAccumulatorMax) + ". ExpScale: " + this.bitrateAdjustmentScaleExp);
            boolean bitrateAdjustmentScaleChanged = DEQUEUE_TIMEOUT;
            if (this.bitrateAccumulator > this.bitrateAccumulatorMax) {
                int bitrateAdjustmentInc = (int) ((this.bitrateAccumulator / this.bitrateAccumulatorMax) + 0.5d);
                this.bitrateAdjustmentScaleExp -= bitrateAdjustmentInc;
                this.bitrateAccumulator = this.bitrateAccumulatorMax;
                bitrateAdjustmentScaleChanged = true;
            } else if (this.bitrateAccumulator < (-this.bitrateAccumulatorMax)) {
                int bitrateAdjustmentInc2 = (int) (((-this.bitrateAccumulator) / this.bitrateAccumulatorMax) + 0.5d);
                this.bitrateAdjustmentScaleExp += bitrateAdjustmentInc2;
                this.bitrateAccumulator = -this.bitrateAccumulatorMax;
                bitrateAdjustmentScaleChanged = true;
            }
            if (bitrateAdjustmentScaleChanged) {
                this.bitrateAdjustmentScaleExp = Math.min(this.bitrateAdjustmentScaleExp, (int) BITRATE_CORRECTION_STEPS);
                this.bitrateAdjustmentScaleExp = Math.max(this.bitrateAdjustmentScaleExp, -20);
                Logging.d(TAG, "Adjusting bitrate scale to " + this.bitrateAdjustmentScaleExp + ". Value: " + getBitrateScale(this.bitrateAdjustmentScaleExp));
                setRates(this.targetBitrateBps / 1000, this.targetFps);
            }
            this.bitrateObservationTimeMs = 0.0d;
        }
    }

    @CalledByNativeUnchecked
    boolean releaseOutputBuffer(int index) {
        checkOnMediaCodecThread();
        try {
            this.mediaCodec.releaseOutputBuffer(index, false);
            return true;
        } catch (IllegalStateException e) {
            Logging.e(TAG, "releaseOutputBuffer failed", e);
            return false;
        }
    }

    @CalledByNative
    int getColorFormat() {
        return this.colorFormat;
    }

    @CalledByNative
    static boolean isTextureBuffer(VideoFrame.Buffer buffer) {
        return buffer instanceof VideoFrame.TextureBuffer;
    }
}
