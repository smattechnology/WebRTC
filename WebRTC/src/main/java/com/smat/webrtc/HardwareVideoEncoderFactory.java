package com.smat.webrtc;

import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.os.Build;
import android.os.Build.VERSION;
import androidx.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class HardwareVideoEncoderFactory implements VideoEncoderFactory {
   private static final String TAG = "HardwareVideoEncoderFactory";
   private static final int QCOM_VP8_KEY_FRAME_INTERVAL_ANDROID_L_MS = 15000;
   private static final int QCOM_VP8_KEY_FRAME_INTERVAL_ANDROID_M_MS = 20000;
   private static final int QCOM_VP8_KEY_FRAME_INTERVAL_ANDROID_N_MS = 15000;
   private static final List<String> H264_HW_EXCEPTION_MODELS = Arrays.asList("SAMSUNG-SGH-I337", "Nexus 7", "Nexus 4");
   @Nullable
   private final EglBase14.Context sharedContext;
   private final boolean enableIntelVp8Encoder;
   private final boolean enableH264HighProfile;
   @Nullable
   private final Predicate<MediaCodecInfo> codecAllowedPredicate;

   public HardwareVideoEncoderFactory(EglBase.Context sharedContext, boolean enableIntelVp8Encoder, boolean enableH264HighProfile) {
      this(sharedContext, enableIntelVp8Encoder, enableH264HighProfile, (Predicate)null);
   }

   public HardwareVideoEncoderFactory(EglBase.Context sharedContext, boolean enableIntelVp8Encoder, boolean enableH264HighProfile, @Nullable Predicate<MediaCodecInfo> codecAllowedPredicate) {
      if (sharedContext instanceof EglBase14.Context) {
         this.sharedContext = (EglBase14.Context)sharedContext;
      } else {
         Logging.w("HardwareVideoEncoderFactory", "No shared EglBase.Context.  Encoders will not use texture mode.");
         this.sharedContext = null;
      }

      this.enableIntelVp8Encoder = enableIntelVp8Encoder;
      this.enableH264HighProfile = enableH264HighProfile;
      this.codecAllowedPredicate = codecAllowedPredicate;
   }

   /** @deprecated */
   @Deprecated
   public HardwareVideoEncoderFactory(boolean enableIntelVp8Encoder, boolean enableH264HighProfile) {
      this((EglBase.Context)null, enableIntelVp8Encoder, enableH264HighProfile);
   }

   @Nullable
   public VideoEncoder createEncoder(VideoCodecInfo input) {
      if (VERSION.SDK_INT < 19) {
         return null;
      } else {
         VideoCodecType type = VideoCodecType.valueOf(input.name);
         MediaCodecInfo info = this.findCodecForType(type);
         if (info == null) {
            return null;
         } else {
            String codecName = info.getName();
            String mime = type.mimeType();
            Integer surfaceColorFormat = MediaCodecUtils.selectColorFormat(MediaCodecUtils.TEXTURE_COLOR_FORMATS, info.getCapabilitiesForType(mime));
            Integer yuvColorFormat = MediaCodecUtils.selectColorFormat(MediaCodecUtils.ENCODER_COLOR_FORMATS, info.getCapabilitiesForType(mime));
            if (type == VideoCodecType.H264) {
               boolean isHighProfile = H264Utils.isSameH264Profile(input.params, MediaCodecUtils.getCodecProperties(type, true));
               boolean isBaselineProfile = H264Utils.isSameH264Profile(input.params, MediaCodecUtils.getCodecProperties(type, false));
               if (!isHighProfile && !isBaselineProfile) {
                  return null;
               }

               if (isHighProfile && !this.isH264HighProfileSupported(info)) {
                  return null;
               }
            }

            return new HardwareVideoEncoder(new MediaCodecWrapperFactoryImpl(), codecName, type, surfaceColorFormat, yuvColorFormat, input.params, this.getKeyFrameIntervalSec(type), this.getForcedKeyFrameIntervalMs(type, codecName), this.createBitrateAdjuster(type, codecName), this.sharedContext);
         }
      }
   }

   public VideoCodecInfo[] getSupportedCodecs() {
      if (VERSION.SDK_INT < 19) {
         return new VideoCodecInfo[0];
      } else {
         List<VideoCodecInfo> supportedCodecInfos = new ArrayList();
         VideoCodecType[] var2 = new VideoCodecType[]{VideoCodecType.VP8, VideoCodecType.VP9, VideoCodecType.H264};
         int var3 = var2.length;

         for(int var4 = 0; var4 < var3; ++var4) {
            VideoCodecType type = var2[var4];
            MediaCodecInfo codec = this.findCodecForType(type);
            if (codec != null) {
               String name = type.name();
               if (type == VideoCodecType.H264 && this.isH264HighProfileSupported(codec)) {
                  supportedCodecInfos.add(new VideoCodecInfo(name, MediaCodecUtils.getCodecProperties(type, true)));
               }

               supportedCodecInfos.add(new VideoCodecInfo(name, MediaCodecUtils.getCodecProperties(type, false)));
            }
         }

         return (VideoCodecInfo[])supportedCodecInfos.toArray(new VideoCodecInfo[supportedCodecInfos.size()]);
      }
   }

   @Nullable
   private MediaCodecInfo findCodecForType(VideoCodecType type) {
      for(int i = 0; i < MediaCodecList.getCodecCount(); ++i) {
         MediaCodecInfo info = null;

         try {
            info = MediaCodecList.getCodecInfoAt(i);
         } catch (IllegalArgumentException var5) {
            Logging.e("HardwareVideoEncoderFactory", "Cannot retrieve encoder codec info", var5);
         }

         if (info != null && info.isEncoder() && this.isSupportedCodec(info, type)) {
            return info;
         }
      }

      return null;
   }

   private boolean isSupportedCodec(MediaCodecInfo info, VideoCodecType type) {
      if (!MediaCodecUtils.codecSupportsType(info, type)) {
         return false;
      } else if (MediaCodecUtils.selectColorFormat(MediaCodecUtils.ENCODER_COLOR_FORMATS, info.getCapabilitiesForType(type.mimeType())) == null) {
         return false;
      } else {
         return this.isHardwareSupportedInCurrentSdk(info, type) && this.isMediaCodecAllowed(info);
      }
   }

   private boolean isHardwareSupportedInCurrentSdk(MediaCodecInfo info, VideoCodecType type) {
      switch(type) {
      case VP8:
         return this.isHardwareSupportedInCurrentSdkVp8(info);
      case VP9:
         return this.isHardwareSupportedInCurrentSdkVp9(info);
      case H264:
         return this.isHardwareSupportedInCurrentSdkH264(info);
      default:
         return false;
      }
   }

   private boolean isHardwareSupportedInCurrentSdkVp8(MediaCodecInfo info) {
      String name = info.getName();
      return name.startsWith("OMX.qcom.") && VERSION.SDK_INT >= 19 || name.startsWith("OMX.Exynos.") && VERSION.SDK_INT >= 23 || name.startsWith("OMX.Intel.") && VERSION.SDK_INT >= 21 && this.enableIntelVp8Encoder;
   }

   private boolean isHardwareSupportedInCurrentSdkVp9(MediaCodecInfo info) {
      String name = info.getName();
      return (name.startsWith("OMX.qcom.") || name.startsWith("OMX.Exynos.")) && VERSION.SDK_INT >= 24;
   }

   private boolean isHardwareSupportedInCurrentSdkH264(MediaCodecInfo info) {
      if (H264_HW_EXCEPTION_MODELS.contains(Build.MODEL)) {
         return false;
      } else {
         String name = info.getName();
         return name.startsWith("OMX.qcom.") && VERSION.SDK_INT >= 19 || name.startsWith("OMX.Exynos.") && VERSION.SDK_INT >= 21;
      }
   }

   private boolean isMediaCodecAllowed(MediaCodecInfo info) {
      return this.codecAllowedPredicate == null ? true : this.codecAllowedPredicate.test(info);
   }

   private int getKeyFrameIntervalSec(VideoCodecType type) {
      switch(type) {
      case VP8:
      case VP9:
         return 100;
      case H264:
         return 20;
      default:
         throw new IllegalArgumentException("Unsupported VideoCodecType " + type);
      }
   }

   private int getForcedKeyFrameIntervalMs(VideoCodecType type, String codecName) {
      if (type == VideoCodecType.VP8 && codecName.startsWith("OMX.qcom.")) {
         if (VERSION.SDK_INT == 21 || VERSION.SDK_INT == 22) {
            return 15000;
         }

         if (VERSION.SDK_INT == 23) {
            return 20000;
         }

         if (VERSION.SDK_INT > 23) {
            return 15000;
         }
      }

      return 0;
   }

   private BitrateAdjuster createBitrateAdjuster(VideoCodecType type, String codecName) {
      if (codecName.startsWith("OMX.Exynos.")) {
         return (BitrateAdjuster)(type == VideoCodecType.VP8 ? new DynamicBitrateAdjuster() : new FramerateBitrateAdjuster());
      } else {
         return new BaseBitrateAdjuster();
      }
   }

   private boolean isH264HighProfileSupported(MediaCodecInfo info) {
      return this.enableH264HighProfile && VERSION.SDK_INT > 23 && info.getName().startsWith("OMX.Exynos.");
   }
}
