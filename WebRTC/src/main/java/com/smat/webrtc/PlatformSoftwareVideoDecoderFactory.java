package com.smat.webrtc;

import android.media.MediaCodecInfo;
import androidx.annotation.Nullable;
import java.util.Arrays;

public class PlatformSoftwareVideoDecoderFactory extends MediaCodecVideoDecoderFactory {
   private static final Predicate<MediaCodecInfo> defaultAllowedPredicate = new Predicate<MediaCodecInfo>() {
      private String[] prefixWhitelist;

      {
         this.prefixWhitelist = (String[])Arrays.copyOf(MediaCodecUtils.SOFTWARE_IMPLEMENTATION_PREFIXES, MediaCodecUtils.SOFTWARE_IMPLEMENTATION_PREFIXES.length);
      }

      public boolean test(MediaCodecInfo arg) {
         String name = arg.getName();
         String[] var3 = this.prefixWhitelist;
         int var4 = var3.length;

         for(int var5 = 0; var5 < var4; ++var5) {
            String prefix = var3[var5];
            if (name.startsWith(prefix)) {
               return true;
            }
         }

         return false;
      }
   };

   public PlatformSoftwareVideoDecoderFactory(@Nullable EglBase.Context sharedContext) {
      super(sharedContext, defaultAllowedPredicate);
   }
}
