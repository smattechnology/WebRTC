package com.smat.webrtc;

import android.annotation.TargetApi;
import android.graphics.Matrix;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaCrypto;
import android.media.MediaFormat;
import android.media.MediaCodec.BufferInfo;
import android.media.MediaCodecInfo.CodecCapabilities;
import android.opengl.GLES20;
import android.os.Build;
import android.os.Bundle;
import android.os.Build.VERSION;
import androidx.annotation.Nullable;
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

/** @deprecated */
@Deprecated
@TargetApi(19)
public class MediaCodecVideoEncoder {
   private static final String TAG = "MediaCodecVideoEncoder";
   private static final int MEDIA_CODEC_RELEASE_TIMEOUT_MS = 5000;
   private static final int DEQUEUE_TIMEOUT = 0;
   private static final int BITRATE_ADJUSTMENT_FPS = 30;
   private static final int MAXIMUM_INITIAL_FPS = 30;
   private static final double BITRATE_CORRECTION_SEC = 3.0D;
   private static final double BITRATE_CORRECTION_MAX_SCALE = 4.0D;
   private static final int BITRATE_CORRECTION_STEPS = 20;
   private static final long QCOM_VP8_KEY_FRAME_INTERVAL_ANDROID_L_MS = 15000L;
   private static final long QCOM_VP8_KEY_FRAME_INTERVAL_ANDROID_M_MS = 20000L;
   private static final long QCOM_VP8_KEY_FRAME_INTERVAL_ANDROID_N_MS = 15000L;
   @Nullable
   private static MediaCodecVideoEncoder runningInstance;
   @Nullable
   private static MediaCodecVideoEncoder.MediaCodecVideoEncoderErrorCallback errorCallback;
   private static int codecErrors;
   private static Set<String> hwEncoderDisabledTypes = new HashSet();
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
   private static final MediaCodecProperties qcomVp8HwProperties;
   private static final MediaCodecProperties exynosVp8HwProperties;
   private static final MediaCodecProperties intelVp8HwProperties;
   private static final MediaCodecProperties qcomVp9HwProperties;
   private static final MediaCodecProperties exynosVp9HwProperties;
   private static final MediaCodecProperties[] vp9HwList;
   private static final MediaCodecProperties qcomH264HwProperties;
   private static final MediaCodecProperties exynosH264HwProperties;
   private static final MediaCodecProperties mediatekH264HwProperties;
   private static final MediaCodecProperties exynosH264HighProfileHwProperties;
   private static final MediaCodecProperties[] h264HighProfileHwList;
   private static final String[] H264_HW_EXCEPTION_MODELS;
   private static final int VIDEO_ControlRateConstant = 2;
   private static final int COLOR_QCOM_FORMATYUV420PackedSemiPlanar32m = 2141391876;
   private static final int[] supportedColorList;
   private static final int[] supportedSurfaceColorList;
   private VideoCodecType type;
   private int colorFormat;
   private BitrateAdjustmentType bitrateAdjustmentType;
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

   public static VideoEncoderFactory createFactory() {
      return new DefaultVideoEncoderFactory(new HwEncoderFactory());
   }

   public static void setEglContext(EglBase.Context eglContext) {
      if (staticEglBase != null) {
         Logging.w("MediaCodecVideoEncoder", "Egl context already set.");
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
      return staticEglBase == null ? null : staticEglBase.getEglBaseContext();
   }

   private static MediaCodecProperties[] vp8HwList() {
      ArrayList<MediaCodecProperties> supported_codecs = new ArrayList();
      supported_codecs.add(qcomVp8HwProperties);
      supported_codecs.add(exynosVp8HwProperties);
      if (PeerConnectionFactory.fieldTrialsFindFullName("WebRTC-IntelVP8").equals("Enabled")) {
         supported_codecs.add(intelVp8HwProperties);
      }

      return (MediaCodecProperties[])supported_codecs.toArray(new MediaCodecProperties[supported_codecs.size()]);
   }

   private static final MediaCodecProperties[] h264HwList() {
      ArrayList<MediaCodecProperties> supported_codecs = new ArrayList();
      supported_codecs.add(qcomH264HwProperties);
      supported_codecs.add(exynosH264HwProperties);
      if (PeerConnectionFactory.fieldTrialsFindFullName("WebRTC-MediaTekH264").equals("Enabled")) {
         supported_codecs.add(mediatekH264HwProperties);
      }

      return (MediaCodecProperties[])supported_codecs.toArray(new MediaCodecProperties[supported_codecs.size()]);
   }

   public static void setErrorCallback(MediaCodecVideoEncoderErrorCallback errorCallback) {
      Logging.d("MediaCodecVideoEncoder", "Set error callback");
      MediaCodecVideoEncoder.errorCallback = errorCallback;
   }

   public static void disableVp8HwCodec() {
      Logging.w("MediaCodecVideoEncoder", "VP8 encoding is disabled by application.");
      hwEncoderDisabledTypes.add("video/x-vnd.on2.vp8");
   }

   public static void disableVp9HwCodec() {
      Logging.w("MediaCodecVideoEncoder", "VP9 encoding is disabled by application.");
      hwEncoderDisabledTypes.add("video/x-vnd.on2.vp9");
   }

   public static void disableH264HwCodec() {
      Logging.w("MediaCodecVideoEncoder", "H.264 encoding is disabled by application.");
      hwEncoderDisabledTypes.add("video/avc");
   }

   public static boolean isVp8HwSupported() {
      return !hwEncoderDisabledTypes.contains("video/x-vnd.on2.vp8") && findHwEncoder("video/x-vnd.on2.vp8", vp8HwList(), supportedColorList) != null;
   }

   @Nullable
   public static MediaCodecVideoEncoder.EncoderProperties vp8HwEncoderProperties() {
      return hwEncoderDisabledTypes.contains("video/x-vnd.on2.vp8") ? null : findHwEncoder("video/x-vnd.on2.vp8", vp8HwList(), supportedColorList);
   }

   public static boolean isVp9HwSupported() {
      return !hwEncoderDisabledTypes.contains("video/x-vnd.on2.vp9") && findHwEncoder("video/x-vnd.on2.vp9", vp9HwList, supportedColorList) != null;
   }

   public static boolean isH264HwSupported() {
      return !hwEncoderDisabledTypes.contains("video/avc") && findHwEncoder("video/avc", h264HwList(), supportedColorList) != null;
   }

   public static boolean isH264HighProfileHwSupported() {
      return !hwEncoderDisabledTypes.contains("video/avc") && findHwEncoder("video/avc", h264HighProfileHwList, supportedColorList) != null;
   }

   public static boolean isVp8HwSupportedUsingTextures() {
      return !hwEncoderDisabledTypes.contains("video/x-vnd.on2.vp8") && findHwEncoder("video/x-vnd.on2.vp8", vp8HwList(), supportedSurfaceColorList) != null;
   }

   public static boolean isVp9HwSupportedUsingTextures() {
      return !hwEncoderDisabledTypes.contains("video/x-vnd.on2.vp9") && findHwEncoder("video/x-vnd.on2.vp9", vp9HwList, supportedSurfaceColorList) != null;
   }

   public static boolean isH264HwSupportedUsingTextures() {
      return !hwEncoderDisabledTypes.contains("video/avc") && findHwEncoder("video/avc", h264HwList(), supportedSurfaceColorList) != null;
   }

   @Nullable
   private static MediaCodecVideoEncoder.EncoderProperties findHwEncoder(String mime, MediaCodecProperties[] supportedHwCodecProperties, int[] colorList) {
      if (VERSION.SDK_INT < 19) {
         return null;
      } else {
         if (mime.equals("video/avc")) {
            List<String> exceptionModels = Arrays.asList(H264_HW_EXCEPTION_MODELS);
            if (exceptionModels.contains(Build.MODEL)) {
               Logging.w("MediaCodecVideoEncoder", "Model: " + Build.MODEL + " has black listed H.264 encoder.");
               return null;
            }
         }

         for(int i = 0; i < MediaCodecList.getCodecCount(); ++i) {
            MediaCodecInfo info = null;

            try {
               info = MediaCodecList.getCodecInfoAt(i);
            } catch (IllegalArgumentException var17) {
               Logging.e("MediaCodecVideoEncoder", "Cannot retrieve encoder codec info", var17);
            }

            if (info != null && info.isEncoder()) {
               String name = null;
               String[] var6 = info.getSupportedTypes();
               int var7 = var6.length;

               for(int var8 = 0; var8 < var7; ++var8) {
                  String mimeType = var6[var8];
                  if (mimeType.equals(mime)) {
                     name = info.getName();
                     break;
                  }
               }

               if (name != null) {
                  Logging.v("MediaCodecVideoEncoder", "Found candidate encoder " + name);
                  boolean supportedCodec = false;
                  BitrateAdjustmentType bitrateAdjustmentType = BitrateAdjustmentType.NO_ADJUSTMENT;
                  MediaCodecProperties[] var22 = supportedHwCodecProperties;
                  int var24 = supportedHwCodecProperties.length;

                  int var10;
                  for(var10 = 0; var10 < var24; ++var10) {
                     MediaCodecProperties codecProperties = var22[var10];
                     if (name.startsWith(codecProperties.codecPrefix)) {
                        if (VERSION.SDK_INT >= codecProperties.minSdk) {
                           if (codecProperties.bitrateAdjustmentType != BitrateAdjustmentType.NO_ADJUSTMENT) {
                              bitrateAdjustmentType = codecProperties.bitrateAdjustmentType;
                              Logging.w("MediaCodecVideoEncoder", "Codec " + name + " requires bitrate adjustment: " + bitrateAdjustmentType);
                           }

                           supportedCodec = true;
                           break;
                        }

                        Logging.w("MediaCodecVideoEncoder", "Codec " + name + " is disabled due to SDK version " + VERSION.SDK_INT);
                     }
                  }

                  if (supportedCodec) {
                     CodecCapabilities capabilities;
                     try {
                        capabilities = info.getCapabilitiesForType(mime);
                     } catch (IllegalArgumentException var18) {
                        Logging.e("MediaCodecVideoEncoder", "Cannot retrieve encoder capabilities", var18);
                        continue;
                     }

                     int[] var25 = capabilities.colorFormats;
                     var10 = var25.length;

                     int supportedColorFormat;
                     int var26;
                     for(var26 = 0; var26 < var10; ++var26) {
                        supportedColorFormat = var25[var26];
                        Logging.v("MediaCodecVideoEncoder", "   Color: 0x" + Integer.toHexString(supportedColorFormat));
                     }

                     var25 = colorList;
                     var10 = colorList.length;

                     for(var26 = 0; var26 < var10; ++var26) {
                        supportedColorFormat = var25[var26];
                        int[] var13 = capabilities.colorFormats;
                        int var14 = var13.length;

                        for(int var15 = 0; var15 < var14; ++var15) {
                           int codecColorFormat = var13[var15];
                           if (codecColorFormat == supportedColorFormat) {
                              Logging.d("MediaCodecVideoEncoder", "Found target encoder for mime " + mime + " : " + name + ". Color: 0x" + Integer.toHexString(codecColorFormat) + ". Bitrate adjustment: " + bitrateAdjustmentType);
                              return new EncoderProperties(name, codecColorFormat, bitrateAdjustmentType);
                           }
                        }
                     }
                  }
               }
            }
         }

         return null;
      }
   }

   @CalledByNative
   MediaCodecVideoEncoder() {
      this.bitrateAdjustmentType = BitrateAdjustmentType.NO_ADJUSTMENT;
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
            Logging.d("MediaCodecVideoEncoder", "MediaCodecVideoEncoder stacks trace:");
            StackTraceElement[] var1 = mediaCodecStackTraces;
            int var2 = mediaCodecStackTraces.length;

            for(int var3 = 0; var3 < var2; ++var3) {
               StackTraceElement stackTrace = var1[var3];
               Logging.d("MediaCodecVideoEncoder", stackTrace.toString());
            }
         }
      }

   }

   @Nullable
   static MediaCodec createByCodecName(String codecName) {
      try {
         return MediaCodec.createByCodecName(codecName);
      } catch (Exception var2) {
         return null;
      }
   }

   @CalledByNativeUnchecked
   boolean initEncode(VideoCodecType type, int profile, int width, int height, int kbps, int fps, boolean useSurface) {
      Logging.d("MediaCodecVideoEncoder", "Java initEncode: " + type + ". Profile: " + profile + " : " + width + " x " + height + ". @ " + kbps + " kbps. Fps: " + fps + ". Encode from texture : " + useSurface);
      this.profile = profile;
      this.width = width;
      this.height = height;
      if (this.mediaCodecThread != null) {
         throw new RuntimeException("Forgot to release()?");
      } else {
         EncoderProperties properties = null;
         String mime = null;
         int keyFrameIntervalSec = false;
         boolean configureH264HighProfile = false;
         byte keyFrameIntervalSec;
         if (type == VideoCodecType.VIDEO_CODEC_VP8) {
            mime = "video/x-vnd.on2.vp8";
            properties = findHwEncoder("video/x-vnd.on2.vp8", vp8HwList(), useSurface ? supportedSurfaceColorList : supportedColorList);
            keyFrameIntervalSec = 100;
         } else if (type == VideoCodecType.VIDEO_CODEC_VP9) {
            mime = "video/x-vnd.on2.vp9";
            properties = findHwEncoder("video/x-vnd.on2.vp9", vp9HwList, useSurface ? supportedSurfaceColorList : supportedColorList);
            keyFrameIntervalSec = 100;
         } else {
            if (type != VideoCodecType.VIDEO_CODEC_H264) {
               throw new RuntimeException("initEncode: Non-supported codec " + type);
            }

            mime = "video/avc";
            properties = findHwEncoder("video/avc", h264HwList(), useSurface ? supportedSurfaceColorList : supportedColorList);
            if (profile == H264Profile.CONSTRAINED_HIGH.getValue()) {
               EncoderProperties h264HighProfileProperties = findHwEncoder("video/avc", h264HighProfileHwList, useSurface ? supportedSurfaceColorList : supportedColorList);
               if (h264HighProfileProperties != null) {
                  Logging.d("MediaCodecVideoEncoder", "High profile H.264 encoder supported.");
                  configureH264HighProfile = true;
               } else {
                  Logging.d("MediaCodecVideoEncoder", "High profile H.264 encoder requested, but not supported. Use baseline.");
               }
            }

            keyFrameIntervalSec = 20;
         }

         if (properties == null) {
            throw new RuntimeException("Can not find HW encoder for " + type);
         } else {
            runningInstance = this;
            this.colorFormat = properties.colorFormat;
            this.bitrateAdjustmentType = properties.bitrateAdjustmentType;
            if (this.bitrateAdjustmentType == BitrateAdjustmentType.FRAMERATE_ADJUSTMENT) {
               fps = 30;
            } else {
               fps = Math.min(fps, 30);
            }

            this.forcedKeyFrameMs = 0L;
            this.lastKeyFrameMs = -1L;
            if (type == VideoCodecType.VIDEO_CODEC_VP8 && properties.codecName.startsWith(qcomVp8HwProperties.codecPrefix)) {
               if (VERSION.SDK_INT != 21 && VERSION.SDK_INT != 22) {
                  if (VERSION.SDK_INT == 23) {
                     this.forcedKeyFrameMs = 20000L;
                  } else if (VERSION.SDK_INT > 23) {
                     this.forcedKeyFrameMs = 15000L;
                  }
               } else {
                  this.forcedKeyFrameMs = 15000L;
               }
            }

            Logging.d("MediaCodecVideoEncoder", "Color format: " + this.colorFormat + ". Bitrate adjustment: " + this.bitrateAdjustmentType + ". Key frame interval: " + this.forcedKeyFrameMs + " . Initial fps: " + fps);
            this.targetBitrateBps = 1000 * kbps;
            this.targetFps = fps;
            this.bitrateAccumulatorMax = (double)this.targetBitrateBps / 8.0D;
            this.bitrateAccumulator = 0.0D;
            this.bitrateObservationTimeMs = 0.0D;
            this.bitrateAdjustmentScaleExp = 0;
            this.mediaCodecThread = Thread.currentThread();

            try {
               MediaFormat format = MediaFormat.createVideoFormat(mime, width, height);
               format.setInteger("bitrate", this.targetBitrateBps);
               format.setInteger("bitrate-mode", 2);
               format.setInteger("color-format", properties.colorFormat);
               format.setInteger("frame-rate", this.targetFps);
               format.setInteger("i-frame-interval", keyFrameIntervalSec);
               if (configureH264HighProfile) {
                  format.setInteger("profile", 8);
                  format.setInteger("level", 256);
               }

               Logging.d("MediaCodecVideoEncoder", "  Format: " + format);
               this.mediaCodec = createByCodecName(properties.codecName);
               this.type = type;
               if (this.mediaCodec == null) {
                  Logging.e("MediaCodecVideoEncoder", "Can not create media encoder");
                  this.release();
                  return false;
               } else {
                  this.mediaCodec.configure(format, (Surface)null, (MediaCrypto)null, 1);
                  if (useSurface) {
                     this.eglBase = EglBase.createEgl14((EglBase14.Context)getEglContext(), EglBase.CONFIG_RECORDABLE);
                     this.inputSurface = this.mediaCodec.createInputSurface();
                     this.eglBase.createSurface(this.inputSurface);
                     this.drawer = new GlRectDrawer();
                  }

                  this.mediaCodec.start();
                  this.outputBuffers = this.mediaCodec.getOutputBuffers();
                  Logging.d("MediaCodecVideoEncoder", "Output buffers: " + this.outputBuffers.length);
                  return true;
               }
            } catch (IllegalStateException var13) {
               Logging.e("MediaCodecVideoEncoder", "initEncode failed", var13);
               this.release();
               return false;
            }
         }
      }
   }

   @CalledByNativeUnchecked
   ByteBuffer[] getInputBuffers() {
      ByteBuffer[] inputBuffers = this.mediaCodec.getInputBuffers();
      Logging.d("MediaCodecVideoEncoder", "Input buffers: " + inputBuffers.length);
      return inputBuffers;
   }

   void checkKeyFrameRequired(boolean requestedKeyFrame, long presentationTimestampUs) {
      long presentationTimestampMs = (presentationTimestampUs + 500L) / 1000L;
      if (this.lastKeyFrameMs < 0L) {
         this.lastKeyFrameMs = presentationTimestampMs;
      }

      boolean forcedKeyFrame = false;
      if (!requestedKeyFrame && this.forcedKeyFrameMs > 0L && presentationTimestampMs > this.lastKeyFrameMs + this.forcedKeyFrameMs) {
         forcedKeyFrame = true;
      }

      if (requestedKeyFrame || forcedKeyFrame) {
         if (requestedKeyFrame) {
            Logging.d("MediaCodecVideoEncoder", "Sync frame request");
         } else {
            Logging.d("MediaCodecVideoEncoder", "Sync frame forced");
         }

         Bundle b = new Bundle();
         b.putInt("request-sync", 0);
         this.mediaCodec.setParameters(b);
         this.lastKeyFrameMs = presentationTimestampMs;
      }

   }

   @CalledByNativeUnchecked
   boolean encodeBuffer(boolean isKeyframe, int inputBuffer, int size, long presentationTimestampUs) {
      this.checkOnMediaCodecThread();

      try {
         this.checkKeyFrameRequired(isKeyframe, presentationTimestampUs);
         this.mediaCodec.queueInputBuffer(inputBuffer, 0, size, presentationTimestampUs, 0);
         return true;
      } catch (IllegalStateException var7) {
         Logging.e("MediaCodecVideoEncoder", "encodeBuffer failed", var7);
         return false;
      }
   }

   @CalledByNativeUnchecked
   boolean encodeFrame(long nativeEncoder, boolean isKeyframe, VideoFrame frame, int bufferIndex, long presentationTimestampUs) {
      this.checkOnMediaCodecThread();

      try {
         this.checkKeyFrameRequired(isKeyframe, presentationTimestampUs);
         VideoFrame.Buffer buffer = frame.getBuffer();
         if (buffer instanceof VideoFrame.TextureBuffer) {
            VideoFrame.TextureBuffer textureBuffer = (VideoFrame.TextureBuffer)buffer;
            this.eglBase.makeCurrent();
            GLES20.glClear(16384);
            VideoFrameDrawer.drawTexture(this.drawer, textureBuffer, new Matrix(), this.width, this.height, 0, 0, this.width, this.height);
            this.eglBase.swapBuffers(TimeUnit.MICROSECONDS.toNanos(presentationTimestampUs));
         } else {
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
            int yuvSize = this.width * this.height * 3 / 2;
            this.mediaCodec.queueInputBuffer(bufferIndex, 0, yuvSize, presentationTimestampUs, 0);
         }

         return true;
      } catch (RuntimeException var18) {
         Logging.e("MediaCodecVideoEncoder", "encodeFrame failed", var18);
         return false;
      }
   }

   @CalledByNativeUnchecked
   void release() {
      Logging.d("MediaCodecVideoEncoder", "Java releaseEncoder");
      this.checkOnMediaCodecThread();

      class CaughtException {
         Exception e;
      }

      final CaughtException caughtException = new CaughtException();
      boolean stopHung = false;
      if (this.mediaCodec != null) {
         final CountDownLatch releaseDone = new CountDownLatch(1);
         Runnable runMediaCodecRelease = new Runnable() {
            public void run() {
               Logging.d("MediaCodecVideoEncoder", "Java releaseEncoder on release thread");

               try {
                  MediaCodecVideoEncoder.this.mediaCodec.stop();
               } catch (Exception var3) {
                  Logging.e("MediaCodecVideoEncoder", "Media encoder stop failed", var3);
               }

               try {
                  MediaCodecVideoEncoder.this.mediaCodec.release();
               } catch (Exception var2) {
                  Logging.e("MediaCodecVideoEncoder", "Media encoder release failed", var2);
                  caughtException.e = var2;
               }

               Logging.d("MediaCodecVideoEncoder", "Java releaseEncoder on release thread done");
               releaseDone.countDown();
            }
         };
         (new Thread(runMediaCodecRelease)).start();
         if (!ThreadUtils.awaitUninterruptibly(releaseDone, 5000L)) {
            Logging.e("MediaCodecVideoEncoder", "Media encoder release timeout");
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
         ++codecErrors;
         if (errorCallback != null) {
            Logging.e("MediaCodecVideoEncoder", "Invoke codec error callback. Errors: " + codecErrors);
            errorCallback.onMediaCodecVideoEncoderCriticalError(codecErrors);
         }

         throw new RuntimeException("Media encoder release timeout.");
      } else if (caughtException.e != null) {
         RuntimeException runtimeException = new RuntimeException(caughtException.e);
         runtimeException.setStackTrace(ThreadUtils.concatStackTraces(caughtException.e.getStackTrace(), runtimeException.getStackTrace()));
         throw runtimeException;
      } else {
         Logging.d("MediaCodecVideoEncoder", "Java releaseEncoder done");
      }
   }

   @CalledByNativeUnchecked
   private boolean setRates(int kbps, int frameRate) {
      this.checkOnMediaCodecThread();
      int codecBitrateBps = 1000 * kbps;
      if (this.bitrateAdjustmentType == BitrateAdjustmentType.DYNAMIC_ADJUSTMENT) {
         this.bitrateAccumulatorMax = (double)codecBitrateBps / 8.0D;
         if (this.targetBitrateBps > 0 && codecBitrateBps < this.targetBitrateBps) {
            this.bitrateAccumulator = this.bitrateAccumulator * (double)codecBitrateBps / (double)this.targetBitrateBps;
         }
      }

      this.targetBitrateBps = codecBitrateBps;
      this.targetFps = frameRate;
      if (this.bitrateAdjustmentType == BitrateAdjustmentType.FRAMERATE_ADJUSTMENT && this.targetFps > 0) {
         codecBitrateBps = 30 * this.targetBitrateBps / this.targetFps;
         Logging.v("MediaCodecVideoEncoder", "setRates: " + kbps + " -> " + codecBitrateBps / 1000 + " kbps. Fps: " + this.targetFps);
      } else if (this.bitrateAdjustmentType == BitrateAdjustmentType.DYNAMIC_ADJUSTMENT) {
         Logging.v("MediaCodecVideoEncoder", "setRates: " + kbps + " kbps. Fps: " + this.targetFps + ". ExpScale: " + this.bitrateAdjustmentScaleExp);
         if (this.bitrateAdjustmentScaleExp != 0) {
            codecBitrateBps = (int)((double)codecBitrateBps * this.getBitrateScale(this.bitrateAdjustmentScaleExp));
         }
      } else {
         Logging.v("MediaCodecVideoEncoder", "setRates: " + kbps + " kbps. Fps: " + this.targetFps);
      }

      try {
         Bundle params = new Bundle();
         params.putInt("video-bitrate", codecBitrateBps);
         this.mediaCodec.setParameters(params);
         return true;
      } catch (IllegalStateException var5) {
         Logging.e("MediaCodecVideoEncoder", "setRates failed", var5);
         return false;
      }
   }

   @CalledByNativeUnchecked
   int dequeueInputBuffer() {
      this.checkOnMediaCodecThread();

      try {
         return this.mediaCodec.dequeueInputBuffer(0L);
      } catch (IllegalStateException var2) {
         Logging.e("MediaCodecVideoEncoder", "dequeueIntputBuffer failed", var2);
         return -2;
      }
   }

   @Nullable
   @CalledByNativeUnchecked
   MediaCodecVideoEncoder.OutputBufferInfo dequeueOutputBuffer() {
      this.checkOnMediaCodecThread();

      try {
         BufferInfo info = new BufferInfo();
         int result = this.mediaCodec.dequeueOutputBuffer(info, 0L);
         if (result >= 0) {
            boolean isConfigFrame = (info.flags & 2) != 0;
            if (isConfigFrame) {
               Logging.d("MediaCodecVideoEncoder", "Config frame generated. Offset: " + info.offset + ". Size: " + info.size);
               this.configData = ByteBuffer.allocateDirect(info.size);
               this.outputBuffers[result].position(info.offset);
               this.outputBuffers[result].limit(info.offset + info.size);
               this.configData.put(this.outputBuffers[result]);
               String spsData = "";

               for(int i = 0; i < (info.size < 8 ? info.size : 8); ++i) {
                  spsData = spsData + Integer.toHexString(this.configData.get(i) & 255) + " ";
               }

               Logging.d("MediaCodecVideoEncoder", spsData);
               this.mediaCodec.releaseOutputBuffer(result, false);
               result = this.mediaCodec.dequeueOutputBuffer(info, 0L);
            }
         }

         if (result >= 0) {
            ByteBuffer outputBuffer = this.outputBuffers[result].duplicate();
            outputBuffer.position(info.offset);
            outputBuffer.limit(info.offset + info.size);
            this.reportEncodedFrame(info.size);
            boolean isKeyFrame = (info.flags & 1) != 0;
            if (isKeyFrame) {
               Logging.d("MediaCodecVideoEncoder", "Sync frame generated");
            }

            if (isKeyFrame && this.type == VideoCodecType.VIDEO_CODEC_H264) {
               Logging.d("MediaCodecVideoEncoder", "Appending config frame of size " + this.configData.capacity() + " to output buffer with offset " + info.offset + ", size " + info.size);
               ByteBuffer keyFrameBuffer = ByteBuffer.allocateDirect(this.configData.capacity() + info.size);
               this.configData.rewind();
               keyFrameBuffer.put(this.configData);
               keyFrameBuffer.put(outputBuffer);
               keyFrameBuffer.position(0);
               return new OutputBufferInfo(result, keyFrameBuffer, isKeyFrame, info.presentationTimeUs);
            } else {
               return new OutputBufferInfo(result, outputBuffer.slice(), isKeyFrame, info.presentationTimeUs);
            }
         } else if (result == -3) {
            this.outputBuffers = this.mediaCodec.getOutputBuffers();
            return this.dequeueOutputBuffer();
         } else if (result == -2) {
            return this.dequeueOutputBuffer();
         } else if (result == -1) {
            return null;
         } else {
            throw new RuntimeException("dequeueOutputBuffer: " + result);
         }
      } catch (IllegalStateException var6) {
         Logging.e("MediaCodecVideoEncoder", "dequeueOutputBuffer failed", var6);
         return new OutputBufferInfo(-1, (ByteBuffer)null, false, -1L);
      }
   }

   private double getBitrateScale(int bitrateAdjustmentScaleExp) {
      return Math.pow(4.0D, (double)bitrateAdjustmentScaleExp / 20.0D);
   }

   private void reportEncodedFrame(int size) {
      if (this.targetFps != 0 && this.bitrateAdjustmentType == BitrateAdjustmentType.DYNAMIC_ADJUSTMENT) {
         double expectedBytesPerFrame = (double)this.targetBitrateBps / (8.0D * (double)this.targetFps);
         this.bitrateAccumulator += (double)size - expectedBytesPerFrame;
         this.bitrateObservationTimeMs += 1000.0D / (double)this.targetFps;
         double bitrateAccumulatorCap = 3.0D * this.bitrateAccumulatorMax;
         this.bitrateAccumulator = Math.min(this.bitrateAccumulator, bitrateAccumulatorCap);
         this.bitrateAccumulator = Math.max(this.bitrateAccumulator, -bitrateAccumulatorCap);
         if (this.bitrateObservationTimeMs > 3000.0D) {
            Logging.d("MediaCodecVideoEncoder", "Acc: " + (int)this.bitrateAccumulator + ". Max: " + (int)this.bitrateAccumulatorMax + ". ExpScale: " + this.bitrateAdjustmentScaleExp);
            boolean bitrateAdjustmentScaleChanged = false;
            int bitrateAdjustmentInc;
            if (this.bitrateAccumulator > this.bitrateAccumulatorMax) {
               bitrateAdjustmentInc = (int)(this.bitrateAccumulator / this.bitrateAccumulatorMax + 0.5D);
               this.bitrateAdjustmentScaleExp -= bitrateAdjustmentInc;
               this.bitrateAccumulator = this.bitrateAccumulatorMax;
               bitrateAdjustmentScaleChanged = true;
            } else if (this.bitrateAccumulator < -this.bitrateAccumulatorMax) {
               bitrateAdjustmentInc = (int)(-this.bitrateAccumulator / this.bitrateAccumulatorMax + 0.5D);
               this.bitrateAdjustmentScaleExp += bitrateAdjustmentInc;
               this.bitrateAccumulator = -this.bitrateAccumulatorMax;
               bitrateAdjustmentScaleChanged = true;
            }

            if (bitrateAdjustmentScaleChanged) {
               this.bitrateAdjustmentScaleExp = Math.min(this.bitrateAdjustmentScaleExp, 20);
               this.bitrateAdjustmentScaleExp = Math.max(this.bitrateAdjustmentScaleExp, -20);
               Logging.d("MediaCodecVideoEncoder", "Adjusting bitrate scale to " + this.bitrateAdjustmentScaleExp + ". Value: " + this.getBitrateScale(this.bitrateAdjustmentScaleExp));
               this.setRates(this.targetBitrateBps / 1000, this.targetFps);
            }

            this.bitrateObservationTimeMs = 0.0D;
         }

      }
   }

   @CalledByNativeUnchecked
   boolean releaseOutputBuffer(int index) {
      this.checkOnMediaCodecThread();

      try {
         this.mediaCodec.releaseOutputBuffer(index, false);
         return true;
      } catch (IllegalStateException var3) {
         Logging.e("MediaCodecVideoEncoder", "releaseOutputBuffer failed", var3);
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

   private static native void nativeFillInputBuffer(long var0, int var2, ByteBuffer var3, int var4, ByteBuffer var5, int var6, ByteBuffer var7, int var8);

   private static native long nativeCreateEncoder(VideoCodecInfo var0, boolean var1);

   static {
      qcomVp8HwProperties = new MediaCodecProperties("OMX.qcom.", 19, BitrateAdjustmentType.NO_ADJUSTMENT);
      exynosVp8HwProperties = new MediaCodecProperties("OMX.Exynos.", 23, BitrateAdjustmentType.DYNAMIC_ADJUSTMENT);
      intelVp8HwProperties = new MediaCodecProperties("OMX.Intel.", 21, BitrateAdjustmentType.NO_ADJUSTMENT);
      qcomVp9HwProperties = new MediaCodecProperties("OMX.qcom.", 24, BitrateAdjustmentType.NO_ADJUSTMENT);
      exynosVp9HwProperties = new MediaCodecProperties("OMX.Exynos.", 24, BitrateAdjustmentType.FRAMERATE_ADJUSTMENT);
      vp9HwList = new MediaCodecProperties[]{qcomVp9HwProperties, exynosVp9HwProperties};
      qcomH264HwProperties = new MediaCodecProperties("OMX.qcom.", 19, BitrateAdjustmentType.NO_ADJUSTMENT);
      exynosH264HwProperties = new MediaCodecProperties("OMX.Exynos.", 21, BitrateAdjustmentType.FRAMERATE_ADJUSTMENT);
      mediatekH264HwProperties = new MediaCodecProperties("OMX.MTK.", 27, BitrateAdjustmentType.FRAMERATE_ADJUSTMENT);
      exynosH264HighProfileHwProperties = new MediaCodecProperties("OMX.Exynos.", 23, BitrateAdjustmentType.FRAMERATE_ADJUSTMENT);
      h264HighProfileHwList = new MediaCodecProperties[]{exynosH264HighProfileHwProperties};
      H264_HW_EXCEPTION_MODELS = new String[]{"SAMSUNG-SGH-I337", "Nexus 7", "Nexus 4"};
      supportedColorList = new int[]{19, 21, 2141391872, 2141391876};
      supportedSurfaceColorList = new int[]{2130708361};
   }

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

   public interface MediaCodecVideoEncoderErrorCallback {
      void onMediaCodecVideoEncoderCriticalError(int var1);
   }

   private static class MediaCodecProperties {
      public final String codecPrefix;
      public final int minSdk;
      public final BitrateAdjustmentType bitrateAdjustmentType;

      MediaCodecProperties(String codecPrefix, int minSdk, BitrateAdjustmentType bitrateAdjustmentType) {
         this.codecPrefix = codecPrefix;
         this.minSdk = minSdk;
         this.bitrateAdjustmentType = bitrateAdjustmentType;
      }
   }

   public static enum H264Profile {
      CONSTRAINED_BASELINE(0),
      BASELINE(1),
      MAIN(2),
      CONSTRAINED_HIGH(3),
      HIGH(4);

      private final int value;

      private H264Profile(int value) {
         this.value = value;
      }

      public int getValue() {
         return this.value;
      }
   }

   public static enum BitrateAdjustmentType {
      NO_ADJUSTMENT,
      FRAMERATE_ADJUSTMENT,
      DYNAMIC_ADJUSTMENT;
   }

   public static enum VideoCodecType {
      VIDEO_CODEC_UNKNOWN,
      VIDEO_CODEC_VP8,
      VIDEO_CODEC_VP9,
      VIDEO_CODEC_H264;

      @CalledByNative("VideoCodecType")
      static VideoCodecType fromNativeIndex(int nativeIndex) {
         return values()[nativeIndex];
      }
   }

   static class HwEncoderFactory implements VideoEncoderFactory {
      private final VideoCodecInfo[] supportedHardwareCodecs = getSupportedHardwareCodecs();

      private static boolean isSameCodec(VideoCodecInfo codecA, VideoCodecInfo codecB) {
         if (!codecA.name.equalsIgnoreCase(codecB.name)) {
            return false;
         } else {
            return codecA.name.equalsIgnoreCase("H264") ? H264Utils.isSameH264Profile(codecA.params, codecB.params) : true;
         }
      }

      private static boolean isCodecSupported(VideoCodecInfo[] supportedCodecs, VideoCodecInfo codec) {
         VideoCodecInfo[] var2 = supportedCodecs;
         int var3 = supportedCodecs.length;

         for(int var4 = 0; var4 < var3; ++var4) {
            VideoCodecInfo supportedCodec = var2[var4];
            if (isSameCodec(supportedCodec, codec)) {
               return true;
            }
         }

         return false;
      }

      private static VideoCodecInfo[] getSupportedHardwareCodecs() {
         List<VideoCodecInfo> codecs = new ArrayList();
         if (MediaCodecVideoEncoder.isVp8HwSupported()) {
            Logging.d("MediaCodecVideoEncoder", "VP8 HW Encoder supported.");
            codecs.add(new VideoCodecInfo("VP8", new HashMap()));
         }

         if (MediaCodecVideoEncoder.isVp9HwSupported()) {
            Logging.d("MediaCodecVideoEncoder", "VP9 HW Encoder supported.");
            codecs.add(new VideoCodecInfo("VP9", new HashMap()));
         }

         if (MediaCodecVideoDecoder.isH264HighProfileHwSupported()) {
            Logging.d("MediaCodecVideoEncoder", "H.264 High Profile HW Encoder supported.");
            codecs.add(H264Utils.DEFAULT_H264_HIGH_PROFILE_CODEC);
         }

         if (MediaCodecVideoEncoder.isH264HwSupported()) {
            Logging.d("MediaCodecVideoEncoder", "H.264 HW Encoder supported.");
            codecs.add(H264Utils.DEFAULT_H264_BASELINE_PROFILE_CODEC);
         }

         return (VideoCodecInfo[])codecs.toArray(new VideoCodecInfo[codecs.size()]);
      }

      public VideoCodecInfo[] getSupportedCodecs() {
         return this.supportedHardwareCodecs;
      }

      @Nullable
      public VideoEncoder createEncoder(final VideoCodecInfo info) {
         if (!isCodecSupported(this.supportedHardwareCodecs, info)) {
            Logging.d("MediaCodecVideoEncoder", "No HW video encoder for codec " + info.name);
            return null;
         } else {
            Logging.d("MediaCodecVideoEncoder", "Create HW video encoder for " + info.name);
            return new WrappedNativeVideoEncoder() {
               public long createNativeVideoEncoder() {
                  return MediaCodecVideoEncoder.nativeCreateEncoder(info, MediaCodecVideoEncoder.staticEglBase instanceof EglBase14);
               }

               public boolean isHardwareEncoder() {
                  return true;
               }
            };
         }
      }
   }
}
