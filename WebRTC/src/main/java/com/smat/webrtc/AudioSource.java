package com.smat.webrtc;

public class AudioSource extends MediaSource {
   public AudioSource(long nativeSource) {
      super(nativeSource);
   }

   long getNativeAudioSource() {
      return this.getNativeMediaSource();
   }
}
