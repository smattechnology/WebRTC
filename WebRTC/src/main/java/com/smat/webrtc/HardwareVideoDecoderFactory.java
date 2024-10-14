package com.smat.webrtc;

import android.media.MediaCodecInfo;
import androidx.annotation.Nullable;
import java.util.Arrays;

public class HardwareVideoDecoderFactory extends MediaCodecVideoDecoderFactory {
   private static final Predicate<MediaCodecInfo> defaultAllowedPredicate = new Predicate<MediaCodecInfo>() {
      private String[] prefixBlacklist;

      {
         this.prefixBlacklist = (String[])Arrays.copyOf(MediaCodecUtils.SOFTWARE_IMPLEMENTATION_PREFIXES, MediaCodecUtils.SOFTWARE_IMPLEMENTATION_PREFIXES.length);
      }

      public boolean test(MediaCodecInfo arg) {
         String name = arg.getName();
         String[] var3 = this.prefixBlacklist;
         int var4 = var3.length;

         for(int var5 = 0; var5 < var4; ++var5) {
            String prefix = var3[var5];
            if (name.startsWith(prefix)) {
               return false;
            }
         }

         return true;
      }
   };

   /** @deprecated */
   @Deprecated
   public HardwareVideoDecoderFactory() {
      this((EglBase.Context)null);
   }

   public HardwareVideoDecoderFactory(@Nullable EglBase.Context sharedContext) {
      this(sharedContext, (Predicate)null);
   }

   public HardwareVideoDecoderFactory(@Nullable EglBase.Context sharedContext, @Nullable Predicate<MediaCodecInfo> codecAllowedPredicate) {
      super(sharedContext, codecAllowedPredicate == null ? defaultAllowedPredicate : codecAllowedPredicate.and(defaultAllowedPredicate));
   }
}
