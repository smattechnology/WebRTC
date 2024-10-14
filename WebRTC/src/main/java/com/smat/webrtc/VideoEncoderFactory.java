package com.smat.webrtc;

import androidx.annotation.Nullable;

public interface VideoEncoderFactory {
   @Nullable
   @CalledByNative
   VideoEncoder createEncoder(VideoCodecInfo var1);

   @CalledByNative
   VideoCodecInfo[] getSupportedCodecs();

   @CalledByNative
   default VideoCodecInfo[] getImplementations() {
      return this.getSupportedCodecs();
   }
}
