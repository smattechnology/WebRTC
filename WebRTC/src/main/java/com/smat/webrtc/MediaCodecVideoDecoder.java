package com.smat.webrtc;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaCrypto;
import android.media.MediaFormat;
import android.media.MediaCodec.BufferInfo;
import android.media.MediaCodec.CodecException;
import android.media.MediaCodecInfo.CodecCapabilities;
import android.os.SystemClock;
import android.os.Build.VERSION;
import androidx.annotation.Nullable;
import android.view.Surface;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** @deprecated */
@Deprecated
public class MediaCodecVideoDecoder {
   private static final String TAG = "MediaCodecVideoDecoder";
   private static final long MAX_DECODE_TIME_MS = 200L;
   private static final String FORMAT_KEY_STRIDE = "stride";
   private static final String FORMAT_KEY_SLICE_HEIGHT = "slice-height";
   private static final String FORMAT_KEY_CROP_LEFT = "crop-left";
   private static final String FORMAT_KEY_CROP_RIGHT = "crop-right";
   private static final String FORMAT_KEY_CROP_TOP = "crop-top";
   private static final String FORMAT_KEY_CROP_BOTTOM = "crop-bottom";
   private static final int DEQUEUE_INPUT_TIMEOUT = 500000;
   private static final int MEDIA_CODEC_RELEASE_TIMEOUT_MS = 5000;
   private static final int MAX_QUEUED_OUTPUTBUFFERS = 3;
   @Nullable
   private static MediaCodecVideoDecoder runningInstance;
   @Nullable
   private static MediaCodecVideoDecoder.MediaCodecVideoDecoderErrorCallback errorCallback;
   private static int codecErrors;
   private static Set<String> hwDecoderDisabledTypes = new HashSet();
   @Nullable
   private static EglBase eglBase;
   @Nullable
   private Thread mediaCodecThread;
   @Nullable
   private MediaCodec mediaCodec;
   private ByteBuffer[] inputBuffers;
   private ByteBuffer[] outputBuffers;
   private static final String VP8_MIME_TYPE = "video/x-vnd.on2.vp8";
   private static final String VP9_MIME_TYPE = "video/x-vnd.on2.vp9";
   private static final String H264_MIME_TYPE = "video/avc";
   private static final String[] supportedVp9HwCodecPrefixes = new String[]{"OMX.qcom.", "OMX.Exynos."};
   private static final String supportedQcomH264HighProfileHwCodecPrefix = "OMX.qcom.";
   private static final String supportedExynosH264HighProfileHwCodecPrefix = "OMX.Exynos.";
   private static final String supportedMediaTekH264HighProfileHwCodecPrefix = "OMX.MTK.";
   private static final int COLOR_QCOM_FORMATYVU420PackedSemiPlanar32m4ka = 2141391873;
   private static final int COLOR_QCOM_FORMATYVU420PackedSemiPlanar16m4ka = 2141391874;
   private static final int COLOR_QCOM_FORMATYVU420PackedSemiPlanar64x32Tile2m8ka = 2141391875;
   private static final int COLOR_QCOM_FORMATYUV420PackedSemiPlanar32m = 2141391876;
   private static final List<Integer> supportedColorList = Arrays.asList(19, 21, 2141391872, 2141391873, 2141391874, 2141391875, 2141391876);
   private int colorFormat;
   private int width;
   private int height;
   private int stride;
   private int sliceHeight;
   private boolean hasDecodedFirstFrame;
   private final Queue<TimeStamps> decodeStartTimeMs = new ArrayDeque();
   @Nullable
   private MediaCodecVideoDecoder.TextureListener textureListener;
   private int droppedFrames;
   @Nullable
   private Surface surface;
   private final Queue<DecodedOutputBuffer> dequeuedSurfaceOutputBuffers = new ArrayDeque();

   public static VideoDecoderFactory createFactory() {
      return new DefaultVideoDecoderFactory(new HwDecoderFactory());
   }

   private static final String[] supportedVp8HwCodecPrefixes() {
      ArrayList<String> supportedPrefixes = new ArrayList();
      supportedPrefixes.add("OMX.qcom.");
      supportedPrefixes.add("OMX.Nvidia.");
      supportedPrefixes.add("OMX.Exynos.");
      supportedPrefixes.add("OMX.Intel.");
      if (PeerConnectionFactory.fieldTrialsFindFullName("WebRTC-MediaTekVP8").equals("Enabled") && VERSION.SDK_INT >= 24) {
         supportedPrefixes.add("OMX.MTK.");
      }

      return (String[])supportedPrefixes.toArray(new String[supportedPrefixes.size()]);
   }

   private static final String[] supportedH264HwCodecPrefixes() {
      ArrayList<String> supportedPrefixes = new ArrayList();
      supportedPrefixes.add("OMX.qcom.");
      supportedPrefixes.add("OMX.Intel.");
      supportedPrefixes.add("OMX.Exynos.");
      if (PeerConnectionFactory.fieldTrialsFindFullName("WebRTC-MediaTekH264").equals("Enabled") && VERSION.SDK_INT >= 27) {
         supportedPrefixes.add("OMX.MTK.");
      }

      return (String[])supportedPrefixes.toArray(new String[supportedPrefixes.size()]);
   }

   public static void setEglContext(EglBase.Context eglContext) {
      if (eglBase != null) {
         Logging.w("MediaCodecVideoDecoder", "Egl context already set.");
         eglBase.release();
      }

      eglBase = EglBase.create(eglContext);
   }

   public static void disposeEglContext() {
      if (eglBase != null) {
         eglBase.release();
         eglBase = null;
      }

   }

   static boolean useSurface() {
      return eglBase != null;
   }

   public static void setErrorCallback(MediaCodecVideoDecoderErrorCallback errorCallback) {
      Logging.d("MediaCodecVideoDecoder", "Set error callback");
      MediaCodecVideoDecoder.errorCallback = errorCallback;
   }

   public static void disableVp8HwCodec() {
      Logging.w("MediaCodecVideoDecoder", "VP8 decoding is disabled by application.");
      hwDecoderDisabledTypes.add("video/x-vnd.on2.vp8");
   }

   public static void disableVp9HwCodec() {
      Logging.w("MediaCodecVideoDecoder", "VP9 decoding is disabled by application.");
      hwDecoderDisabledTypes.add("video/x-vnd.on2.vp9");
   }

   public static void disableH264HwCodec() {
      Logging.w("MediaCodecVideoDecoder", "H.264 decoding is disabled by application.");
      hwDecoderDisabledTypes.add("video/avc");
   }

   public static boolean isVp8HwSupported() {
      return !hwDecoderDisabledTypes.contains("video/x-vnd.on2.vp8") && findDecoder("video/x-vnd.on2.vp8", supportedVp8HwCodecPrefixes()) != null;
   }

   public static boolean isVp9HwSupported() {
      return !hwDecoderDisabledTypes.contains("video/x-vnd.on2.vp9") && findDecoder("video/x-vnd.on2.vp9", supportedVp9HwCodecPrefixes) != null;
   }

   public static boolean isH264HwSupported() {
      return !hwDecoderDisabledTypes.contains("video/avc") && findDecoder("video/avc", supportedH264HwCodecPrefixes()) != null;
   }

   public static boolean isH264HighProfileHwSupported() {
      if (hwDecoderDisabledTypes.contains("video/avc")) {
         return false;
      } else if (VERSION.SDK_INT >= 21 && findDecoder("video/avc", new String[]{"OMX.qcom."}) != null) {
         return true;
      } else if (VERSION.SDK_INT >= 23 && findDecoder("video/avc", new String[]{"OMX.Exynos."}) != null) {
         return true;
      } else {
         return PeerConnectionFactory.fieldTrialsFindFullName("WebRTC-MediaTekH264").equals("Enabled") && VERSION.SDK_INT >= 27 && findDecoder("video/avc", new String[]{"OMX.MTK."}) != null;
      }
   }

   public static void printStackTrace() {
      if (runningInstance != null && runningInstance.mediaCodecThread != null) {
         StackTraceElement[] mediaCodecStackTraces = runningInstance.mediaCodecThread.getStackTrace();
         if (mediaCodecStackTraces.length > 0) {
            Logging.d("MediaCodecVideoDecoder", "MediaCodecVideoDecoder stacks trace:");
            StackTraceElement[] var1 = mediaCodecStackTraces;
            int var2 = mediaCodecStackTraces.length;

            for(int var3 = 0; var3 < var2; ++var3) {
               StackTraceElement stackTrace = var1[var3];
               Logging.d("MediaCodecVideoDecoder", stackTrace.toString());
            }
         }
      }

   }

   @Nullable
   private static MediaCodecVideoDecoder.DecoderProperties findDecoder(String mime, String[] supportedCodecPrefixes) {
      if (VERSION.SDK_INT < 19) {
         return null;
      } else {
         Logging.d("MediaCodecVideoDecoder", "Trying to find HW decoder for mime " + mime);

         for(int i = 0; i < MediaCodecList.getCodecCount(); ++i) {
            MediaCodecInfo info = null;

            try {
               info = MediaCodecList.getCodecInfoAt(i);
            } catch (IllegalArgumentException var13) {
               Logging.e("MediaCodecVideoDecoder", "Cannot retrieve decoder codec info", var13);
            }

            if (info != null && !info.isEncoder()) {
               String name = null;
               String[] var5 = info.getSupportedTypes();
               int var6 = var5.length;

               int var7;
               for(var7 = 0; var7 < var6; ++var7) {
                  String mimeType = var5[var7];
                  if (mimeType.equals(mime)) {
                     name = info.getName();
                     break;
                  }
               }

               if (name != null) {
                  Logging.d("MediaCodecVideoDecoder", "Found candidate decoder " + name);
                  boolean supportedCodec = false;
                  String[] var16 = supportedCodecPrefixes;
                  var7 = supportedCodecPrefixes.length;

                  int supportedColorFormat;
                  for(supportedColorFormat = 0; supportedColorFormat < var7; ++supportedColorFormat) {
                     String codecPrefix = var16[supportedColorFormat];
                     if (name.startsWith(codecPrefix)) {
                        supportedCodec = true;
                        break;
                     }
                  }

                  if (supportedCodec) {
                     CodecCapabilities capabilities;
                     try {
                        capabilities = info.getCapabilitiesForType(mime);
                     } catch (IllegalArgumentException var14) {
                        Logging.e("MediaCodecVideoDecoder", "Cannot retrieve decoder capabilities", var14);
                        continue;
                     }

                     int[] var18 = capabilities.colorFormats;
                     supportedColorFormat = var18.length;

                     int colorFormat;
                     for(int var21 = 0; var21 < supportedColorFormat; ++var21) {
                        colorFormat = var18[var21];
                        Logging.v("MediaCodecVideoDecoder", "   Color: 0x" + Integer.toHexString(colorFormat));
                     }

                     Iterator var20 = supportedColorList.iterator();

                     while(var20.hasNext()) {
                        supportedColorFormat = (Integer)var20.next();
                        int[] var22 = capabilities.colorFormats;
                        colorFormat = var22.length;

                        for(int var11 = 0; var11 < colorFormat; ++var11) {
                           int codecColorFormat = var22[var11];
                           if (codecColorFormat == supportedColorFormat) {
                              Logging.d("MediaCodecVideoDecoder", "Found target decoder " + name + ". Color: 0x" + Integer.toHexString(codecColorFormat));
                              return new DecoderProperties(name, codecColorFormat);
                           }
                        }
                     }
                  }
               }
            }
         }

         Logging.d("MediaCodecVideoDecoder", "No HW decoder found for mime " + mime);
         return null;
      }
   }

   @CalledByNative
   MediaCodecVideoDecoder() {
   }

   private void checkOnMediaCodecThread() throws IllegalStateException {
      if (this.mediaCodecThread.getId() != Thread.currentThread().getId()) {
         throw new IllegalStateException("MediaCodecVideoDecoder previously operated on " + this.mediaCodecThread + " but is now called on " + Thread.currentThread());
      }
   }

   @CalledByNativeUnchecked
   private boolean initDecode(VideoCodecType type, int width, int height) {
      if (this.mediaCodecThread != null) {
         throw new RuntimeException("initDecode: Forgot to release()?");
      } else {
         String mime = null;
         String[] supportedCodecPrefixes = null;
         if (type == VideoCodecType.VIDEO_CODEC_VP8) {
            mime = "video/x-vnd.on2.vp8";
            supportedCodecPrefixes = supportedVp8HwCodecPrefixes();
         } else if (type == VideoCodecType.VIDEO_CODEC_VP9) {
            mime = "video/x-vnd.on2.vp9";
            supportedCodecPrefixes = supportedVp9HwCodecPrefixes;
         } else {
            if (type != VideoCodecType.VIDEO_CODEC_H264) {
               throw new RuntimeException("initDecode: Non-supported codec " + type);
            }

            mime = "video/avc";
            supportedCodecPrefixes = supportedH264HwCodecPrefixes();
         }

         DecoderProperties properties = findDecoder(mime, supportedCodecPrefixes);
         if (properties == null) {
            throw new RuntimeException("Cannot find HW decoder for " + type);
         } else {
            Logging.d("MediaCodecVideoDecoder", "Java initDecode: " + type + " : " + width + " x " + height + ". Color: 0x" + Integer.toHexString(properties.colorFormat) + ". Use Surface: " + useSurface());
            runningInstance = this;
            this.mediaCodecThread = Thread.currentThread();

            try {
               this.width = width;
               this.height = height;
               this.stride = width;
               this.sliceHeight = height;
               if (useSurface()) {
                  SurfaceTextureHelper surfaceTextureHelper = SurfaceTextureHelper.create("Decoder SurfaceTextureHelper", eglBase.getEglBaseContext());
                  if (surfaceTextureHelper != null) {
                     this.textureListener = new TextureListener(surfaceTextureHelper);
                     this.textureListener.setSize(width, height);
                     this.surface = new Surface(surfaceTextureHelper.getSurfaceTexture());
                  }
               }

               MediaFormat format = MediaFormat.createVideoFormat(mime, width, height);
               if (!useSurface()) {
                  format.setInteger("color-format", properties.colorFormat);
               }

               Logging.d("MediaCodecVideoDecoder", "  Format: " + format);
               this.mediaCodec = MediaCodecVideoEncoder.createByCodecName(properties.codecName);
               if (this.mediaCodec == null) {
                  Logging.e("MediaCodecVideoDecoder", "Can not create media decoder");
                  return false;
               } else {
                  this.mediaCodec.configure(format, this.surface, (MediaCrypto)null, 0);
                  this.mediaCodec.start();
                  this.colorFormat = properties.colorFormat;
                  this.outputBuffers = this.mediaCodec.getOutputBuffers();
                  this.inputBuffers = this.mediaCodec.getInputBuffers();
                  this.decodeStartTimeMs.clear();
                  this.hasDecodedFirstFrame = false;
                  this.dequeuedSurfaceOutputBuffers.clear();
                  this.droppedFrames = 0;
                  Logging.d("MediaCodecVideoDecoder", "Input buffers: " + this.inputBuffers.length + ". Output buffers: " + this.outputBuffers.length);
                  return true;
               }
            } catch (IllegalStateException var8) {
               Logging.e("MediaCodecVideoDecoder", "initDecode failed", var8);
               return false;
            }
         }
      }
   }

   @CalledByNativeUnchecked
   private void reset(int width, int height) {
      if (this.mediaCodecThread != null && this.mediaCodec != null) {
         Logging.d("MediaCodecVideoDecoder", "Java reset: " + width + " x " + height);
         this.mediaCodec.flush();
         this.width = width;
         this.height = height;
         if (this.textureListener != null) {
            this.textureListener.setSize(width, height);
         }

         this.decodeStartTimeMs.clear();
         this.dequeuedSurfaceOutputBuffers.clear();
         this.hasDecodedFirstFrame = false;
         this.droppedFrames = 0;
      } else {
         throw new RuntimeException("Incorrect reset call for non-initialized decoder.");
      }
   }

   @CalledByNativeUnchecked
   private void release() {
      Logging.d("MediaCodecVideoDecoder", "Java releaseDecoder. Total number of dropped frames: " + this.droppedFrames);
      this.checkOnMediaCodecThread();
      final CountDownLatch releaseDone = new CountDownLatch(1);
      Runnable runMediaCodecRelease = new Runnable() {
         public void run() {
            try {
               Logging.d("MediaCodecVideoDecoder", "Java releaseDecoder on release thread");
               MediaCodecVideoDecoder.this.mediaCodec.stop();
               MediaCodecVideoDecoder.this.mediaCodec.release();
               Logging.d("MediaCodecVideoDecoder", "Java releaseDecoder on release thread done");
            } catch (Exception var2) {
               Logging.e("MediaCodecVideoDecoder", "Media decoder release failed", var2);
            }

            releaseDone.countDown();
         }
      };
      (new Thread(runMediaCodecRelease)).start();
      if (!ThreadUtils.awaitUninterruptibly(releaseDone, 5000L)) {
         Logging.e("MediaCodecVideoDecoder", "Media decoder release timeout");
         ++codecErrors;
         if (errorCallback != null) {
            Logging.e("MediaCodecVideoDecoder", "Invoke codec error callback. Errors: " + codecErrors);
            errorCallback.onMediaCodecVideoDecoderCriticalError(codecErrors);
         }
      }

      this.mediaCodec = null;
      this.mediaCodecThread = null;
      runningInstance = null;
      if (useSurface()) {
         this.surface.release();
         this.surface = null;
         this.textureListener.release();
      }

      Logging.d("MediaCodecVideoDecoder", "Java releaseDecoder done");
   }

   @CalledByNativeUnchecked
   private int dequeueInputBuffer() {
      this.checkOnMediaCodecThread();

      try {
         return this.mediaCodec.dequeueInputBuffer(500000L);
      } catch (IllegalStateException var2) {
         Logging.e("MediaCodecVideoDecoder", "dequeueIntputBuffer failed", var2);
         return -2;
      }
   }

   @CalledByNativeUnchecked
   private boolean queueInputBuffer(int inputBufferIndex, int size, long presentationTimeStamUs, long timeStampMs, long ntpTimeStamp) {
      this.checkOnMediaCodecThread();

      try {
         this.inputBuffers[inputBufferIndex].position(0);
         this.inputBuffers[inputBufferIndex].limit(size);
         this.decodeStartTimeMs.add(new TimeStamps(SystemClock.elapsedRealtime(), timeStampMs, ntpTimeStamp));
         this.mediaCodec.queueInputBuffer(inputBufferIndex, 0, size, presentationTimeStamUs, 0);
         return true;
      } catch (IllegalStateException var10) {
         Logging.e("MediaCodecVideoDecoder", "decode failed", var10);
         return false;
      }
   }

   @CalledByNativeUnchecked
   @Nullable
   private MediaCodecVideoDecoder.DecodedOutputBuffer dequeueOutputBuffer(int dequeueTimeoutMs) {
      this.checkOnMediaCodecThread();
      if (this.decodeStartTimeMs.isEmpty()) {
         return null;
      } else {
         BufferInfo info = new BufferInfo();

         while(true) {
            int result = this.mediaCodec.dequeueOutputBuffer(info, TimeUnit.MILLISECONDS.toMicros((long)dequeueTimeoutMs));
            switch(result) {
            case -3:
               this.outputBuffers = this.mediaCodec.getOutputBuffers();
               Logging.d("MediaCodecVideoDecoder", "Decoder output buffers changed: " + this.outputBuffers.length);
               if (this.hasDecodedFirstFrame) {
                  throw new RuntimeException("Unexpected output buffer change event.");
               }
               break;
            case -2:
               MediaFormat format = this.mediaCodec.getOutputFormat();
               Logging.d("MediaCodecVideoDecoder", "Decoder format changed: " + format.toString());
               int newWidth;
               int newHeight;
               if (format.containsKey("crop-left") && format.containsKey("crop-right") && format.containsKey("crop-bottom") && format.containsKey("crop-top")) {
                  newWidth = 1 + format.getInteger("crop-right") - format.getInteger("crop-left");
                  newHeight = 1 + format.getInteger("crop-bottom") - format.getInteger("crop-top");
               } else {
                  newWidth = format.getInteger("width");
                  newHeight = format.getInteger("height");
               }

               if (this.hasDecodedFirstFrame && (newWidth != this.width || newHeight != this.height)) {
                  throw new RuntimeException("Unexpected size change. Configured " + this.width + "*" + this.height + ". New " + newWidth + "*" + newHeight);
               }

               this.width = newWidth;
               this.height = newHeight;
               if (this.textureListener != null) {
                  this.textureListener.setSize(this.width, this.height);
               }

               if (!useSurface() && format.containsKey("color-format")) {
                  this.colorFormat = format.getInteger("color-format");
                  Logging.d("MediaCodecVideoDecoder", "Color: 0x" + Integer.toHexString(this.colorFormat));
                  if (!supportedColorList.contains(this.colorFormat)) {
                     throw new IllegalStateException("Non supported color format: " + this.colorFormat);
                  }
               }

               if (format.containsKey("stride")) {
                  this.stride = format.getInteger("stride");
               }

               if (format.containsKey("slice-height")) {
                  this.sliceHeight = format.getInteger("slice-height");
               }

               Logging.d("MediaCodecVideoDecoder", "Frame stride and slice height: " + this.stride + " x " + this.sliceHeight);
               this.stride = Math.max(this.width, this.stride);
               this.sliceHeight = Math.max(this.height, this.sliceHeight);
               break;
            case -1:
               return null;
            default:
               this.hasDecodedFirstFrame = true;
               TimeStamps timeStamps = (TimeStamps)this.decodeStartTimeMs.remove();
               long decodeTimeMs = SystemClock.elapsedRealtime() - timeStamps.decodeStartTimeMs;
               if (decodeTimeMs > 200L) {
                  Logging.e("MediaCodecVideoDecoder", "Very high decode time: " + decodeTimeMs + "ms. Q size: " + this.decodeStartTimeMs.size() + ". Might be caused by resuming H264 decoding after a pause.");
                  decodeTimeMs = 200L;
               }

               return new DecodedOutputBuffer(result, info.offset, info.size, TimeUnit.MICROSECONDS.toMillis(info.presentationTimeUs), timeStamps.timeStampMs, timeStamps.ntpTimeStampMs, decodeTimeMs, SystemClock.elapsedRealtime());
            }
         }
      }
   }

   @CalledByNativeUnchecked
   @Nullable
   private MediaCodecVideoDecoder.DecodedTextureBuffer dequeueTextureBuffer(int dequeueTimeoutMs) {
      this.checkOnMediaCodecThread();
      if (!useSurface()) {
         throw new IllegalStateException("dequeueTexture() called for byte buffer decoding.");
      } else {
         DecodedOutputBuffer outputBuffer = this.dequeueOutputBuffer(dequeueTimeoutMs);
         if (outputBuffer != null) {
            this.dequeuedSurfaceOutputBuffers.add(outputBuffer);
         }

         this.MaybeRenderDecodedTextureBuffer();
         DecodedTextureBuffer renderedBuffer = this.textureListener.dequeueTextureBuffer(dequeueTimeoutMs);
         if (renderedBuffer != null) {
            this.MaybeRenderDecodedTextureBuffer();
            return renderedBuffer;
         } else if (this.dequeuedSurfaceOutputBuffers.size() < Math.min(3, this.outputBuffers.length) && (dequeueTimeoutMs <= 0 || this.dequeuedSurfaceOutputBuffers.isEmpty())) {
            return null;
         } else {
            ++this.droppedFrames;
            DecodedOutputBuffer droppedFrame = (DecodedOutputBuffer)this.dequeuedSurfaceOutputBuffers.remove();
            if (dequeueTimeoutMs > 0) {
               Logging.w("MediaCodecVideoDecoder", "Draining decoder. Dropping frame with TS: " + droppedFrame.presentationTimeStampMs + ". Total number of dropped frames: " + this.droppedFrames);
            } else {
               Logging.w("MediaCodecVideoDecoder", "Too many output buffers " + this.dequeuedSurfaceOutputBuffers.size() + ". Dropping frame with TS: " + droppedFrame.presentationTimeStampMs + ". Total number of dropped frames: " + this.droppedFrames);
            }

            this.mediaCodec.releaseOutputBuffer(droppedFrame.index, false);
            return new DecodedTextureBuffer((VideoFrame.Buffer)null, droppedFrame.presentationTimeStampMs, droppedFrame.timeStampMs, droppedFrame.ntpTimeStampMs, droppedFrame.decodeTimeMs, SystemClock.elapsedRealtime() - droppedFrame.endDecodeTimeMs);
         }
      }
   }

   private void MaybeRenderDecodedTextureBuffer() {
      if (!this.dequeuedSurfaceOutputBuffers.isEmpty() && !this.textureListener.isWaitingForTexture()) {
         DecodedOutputBuffer buffer = (DecodedOutputBuffer)this.dequeuedSurfaceOutputBuffers.remove();
         this.textureListener.addBufferToRender(buffer);
         this.mediaCodec.releaseOutputBuffer(buffer.index, true);
      }
   }

   @CalledByNativeUnchecked
   private void returnDecodedOutputBuffer(int index) throws IllegalStateException, CodecException {
      this.checkOnMediaCodecThread();
      if (useSurface()) {
         throw new IllegalStateException("returnDecodedOutputBuffer() called for surface decoding.");
      } else {
         this.mediaCodec.releaseOutputBuffer(index, false);
      }
   }

   @CalledByNative
   ByteBuffer[] getInputBuffers() {
      return this.inputBuffers;
   }

   @CalledByNative
   ByteBuffer[] getOutputBuffers() {
      return this.outputBuffers;
   }

   @CalledByNative
   int getColorFormat() {
      return this.colorFormat;
   }

   @CalledByNative
   int getWidth() {
      return this.width;
   }

   @CalledByNative
   int getHeight() {
      return this.height;
   }

   @CalledByNative
   int getStride() {
      return this.stride;
   }

   @CalledByNative
   int getSliceHeight() {
      return this.sliceHeight;
   }

   private static native long nativeCreateDecoder(String var0, boolean var1);

   private class TextureListener implements VideoSink {
      private final SurfaceTextureHelper surfaceTextureHelper;
      private final Object newFrameLock = new Object();
      @Nullable
      private MediaCodecVideoDecoder.DecodedOutputBuffer bufferToRender;
      @Nullable
      private MediaCodecVideoDecoder.DecodedTextureBuffer renderedBuffer;

      public TextureListener(SurfaceTextureHelper surfaceTextureHelper) {
         this.surfaceTextureHelper = surfaceTextureHelper;
         surfaceTextureHelper.startListening(this);
      }

      public void addBufferToRender(DecodedOutputBuffer buffer) {
         if (this.bufferToRender != null) {
            Logging.e("MediaCodecVideoDecoder", "Unexpected addBufferToRender() called while waiting for a texture.");
            throw new IllegalStateException("Waiting for a texture.");
         } else {
            this.bufferToRender = buffer;
         }
      }

      public boolean isWaitingForTexture() {
         synchronized(this.newFrameLock) {
            return this.bufferToRender != null;
         }
      }

      public void setSize(int width, int height) {
         this.surfaceTextureHelper.setTextureSize(width, height);
      }

      public void onFrame(VideoFrame frame) {
         synchronized(this.newFrameLock) {
            if (this.renderedBuffer != null) {
               Logging.e("MediaCodecVideoDecoder", "Unexpected onFrame() called while already holding a texture.");
               throw new IllegalStateException("Already holding a texture.");
            } else {
               VideoFrame.Buffer buffer = frame.getBuffer();
               buffer.retain();
               this.renderedBuffer = new DecodedTextureBuffer(buffer, this.bufferToRender.presentationTimeStampMs, this.bufferToRender.timeStampMs, this.bufferToRender.ntpTimeStampMs, this.bufferToRender.decodeTimeMs, SystemClock.elapsedRealtime() - this.bufferToRender.endDecodeTimeMs);
               this.bufferToRender = null;
               this.newFrameLock.notifyAll();
            }
         }
      }

      @Nullable
      public MediaCodecVideoDecoder.DecodedTextureBuffer dequeueTextureBuffer(int timeoutMs) {
         synchronized(this.newFrameLock) {
            if (this.renderedBuffer == null && timeoutMs > 0 && this.isWaitingForTexture()) {
               try {
                  this.newFrameLock.wait((long)timeoutMs);
               } catch (InterruptedException var5) {
                  Thread.currentThread().interrupt();
               }
            }

            DecodedTextureBuffer returnedBuffer = this.renderedBuffer;
            this.renderedBuffer = null;
            return returnedBuffer;
         }
      }

      public void release() {
         this.surfaceTextureHelper.stopListening();
         synchronized(this.newFrameLock) {
            if (this.renderedBuffer != null) {
               this.renderedBuffer.getVideoFrameBuffer().release();
               this.renderedBuffer = null;
            }
         }

         this.surfaceTextureHelper.dispose();
      }
   }

   private static class DecodedTextureBuffer {
      private final VideoFrame.Buffer videoFrameBuffer;
      private final long presentationTimeStampMs;
      private final long timeStampMs;
      private final long ntpTimeStampMs;
      private final long decodeTimeMs;
      private final long frameDelayMs;

      public DecodedTextureBuffer(VideoFrame.Buffer videoFrameBuffer, long presentationTimeStampMs, long timeStampMs, long ntpTimeStampMs, long decodeTimeMs, long frameDelay) {
         this.videoFrameBuffer = videoFrameBuffer;
         this.presentationTimeStampMs = presentationTimeStampMs;
         this.timeStampMs = timeStampMs;
         this.ntpTimeStampMs = ntpTimeStampMs;
         this.decodeTimeMs = decodeTimeMs;
         this.frameDelayMs = frameDelay;
      }

      @CalledByNative("DecodedTextureBuffer")
      VideoFrame.Buffer getVideoFrameBuffer() {
         return this.videoFrameBuffer;
      }

      @CalledByNative("DecodedTextureBuffer")
      long getPresentationTimestampMs() {
         return this.presentationTimeStampMs;
      }

      @CalledByNative("DecodedTextureBuffer")
      long getTimeStampMs() {
         return this.timeStampMs;
      }

      @CalledByNative("DecodedTextureBuffer")
      long getNtpTimestampMs() {
         return this.ntpTimeStampMs;
      }

      @CalledByNative("DecodedTextureBuffer")
      long getDecodeTimeMs() {
         return this.decodeTimeMs;
      }

      @CalledByNative("DecodedTextureBuffer")
      long getFrameDelayMs() {
         return this.frameDelayMs;
      }
   }

   private static class DecodedOutputBuffer {
      private final int index;
      private final int offset;
      private final int size;
      private final long presentationTimeStampMs;
      private final long timeStampMs;
      private final long ntpTimeStampMs;
      private final long decodeTimeMs;
      private final long endDecodeTimeMs;

      public DecodedOutputBuffer(int index, int offset, int size, long presentationTimeStampMs, long timeStampMs, long ntpTimeStampMs, long decodeTime, long endDecodeTime) {
         this.index = index;
         this.offset = offset;
         this.size = size;
         this.presentationTimeStampMs = presentationTimeStampMs;
         this.timeStampMs = timeStampMs;
         this.ntpTimeStampMs = ntpTimeStampMs;
         this.decodeTimeMs = decodeTime;
         this.endDecodeTimeMs = endDecodeTime;
      }

      @CalledByNative("DecodedOutputBuffer")
      int getIndex() {
         return this.index;
      }

      @CalledByNative("DecodedOutputBuffer")
      int getOffset() {
         return this.offset;
      }

      @CalledByNative("DecodedOutputBuffer")
      int getSize() {
         return this.size;
      }

      @CalledByNative("DecodedOutputBuffer")
      long getPresentationTimestampMs() {
         return this.presentationTimeStampMs;
      }

      @CalledByNative("DecodedOutputBuffer")
      long getTimestampMs() {
         return this.timeStampMs;
      }

      @CalledByNative("DecodedOutputBuffer")
      long getNtpTimestampMs() {
         return this.ntpTimeStampMs;
      }

      @CalledByNative("DecodedOutputBuffer")
      long getDecodeTimeMs() {
         return this.decodeTimeMs;
      }
   }

   private static class TimeStamps {
      private final long decodeStartTimeMs;
      private final long timeStampMs;
      private final long ntpTimeStampMs;

      public TimeStamps(long decodeStartTimeMs, long timeStampMs, long ntpTimeStampMs) {
         this.decodeStartTimeMs = decodeStartTimeMs;
         this.timeStampMs = timeStampMs;
         this.ntpTimeStampMs = ntpTimeStampMs;
      }
   }

   private static class DecoderProperties {
      public final String codecName;
      public final int colorFormat;

      public DecoderProperties(String codecName, int colorFormat) {
         this.codecName = codecName;
         this.colorFormat = colorFormat;
      }
   }

   public interface MediaCodecVideoDecoderErrorCallback {
      void onMediaCodecVideoDecoderCriticalError(int var1);
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

   static class HwDecoderFactory implements VideoDecoderFactory {
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
         if (MediaCodecVideoDecoder.isVp8HwSupported()) {
            Logging.d("MediaCodecVideoDecoder", "VP8 HW Decoder supported.");
            codecs.add(new VideoCodecInfo("VP8", new HashMap()));
         }

         if (MediaCodecVideoDecoder.isVp9HwSupported()) {
            Logging.d("MediaCodecVideoDecoder", "VP9 HW Decoder supported.");
            codecs.add(new VideoCodecInfo("VP9", new HashMap()));
         }

         if (MediaCodecVideoDecoder.isH264HighProfileHwSupported()) {
            Logging.d("MediaCodecVideoDecoder", "H.264 High Profile HW Decoder supported.");
            codecs.add(H264Utils.DEFAULT_H264_HIGH_PROFILE_CODEC);
         }

         if (MediaCodecVideoDecoder.isH264HwSupported()) {
            Logging.d("MediaCodecVideoDecoder", "H.264 HW Decoder supported.");
            codecs.add(H264Utils.DEFAULT_H264_BASELINE_PROFILE_CODEC);
         }

         return (VideoCodecInfo[])codecs.toArray(new VideoCodecInfo[codecs.size()]);
      }

      public VideoCodecInfo[] getSupportedCodecs() {
         return this.supportedHardwareCodecs;
      }

      @Nullable
      public VideoDecoder createDecoder(final VideoCodecInfo codec) {
         if (!isCodecSupported(this.supportedHardwareCodecs, codec)) {
            Logging.d("MediaCodecVideoDecoder", "No HW video decoder for codec " + codec.name);
            return null;
         } else {
            Logging.d("MediaCodecVideoDecoder", "Create HW video decoder for " + codec.name);
            return new WrappedNativeVideoDecoder() {
               public long createNativeVideoDecoder() {
                  return MediaCodecVideoDecoder.nativeCreateDecoder(codec.name, MediaCodecVideoDecoder.useSurface());
               }
            };
         }
      }
   }
}
