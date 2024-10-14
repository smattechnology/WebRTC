package com.smat.webrtc;

public class MediaSource {
   private long nativeSource;

   public MediaSource(long nativeSource) {
      this.nativeSource = nativeSource;
   }

   public State state() {
      this.checkMediaSourceExists();
      return nativeGetState(this.nativeSource);
   }

   public void dispose() {
      this.checkMediaSourceExists();
      JniCommon.nativeReleaseRef(this.nativeSource);
      this.nativeSource = 0L;
   }

   protected long getNativeMediaSource() {
      this.checkMediaSourceExists();
      return this.nativeSource;
   }

   private void checkMediaSourceExists() {
      if (this.nativeSource == 0L) {
         throw new IllegalStateException("MediaSource has been disposed.");
      }
   }

   private static native State nativeGetState(long var0);

   public static enum State {
      INITIALIZING,
      LIVE,
      ENDED,
      MUTED;

      @CalledByNative("State")
      static State fromNativeIndex(int nativeIndex) {
         return values()[nativeIndex];
      }
   }
}
