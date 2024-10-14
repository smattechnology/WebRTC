package com.smat.webrtc.audio;

public interface AudioDeviceModule {
   long getNativeAudioDeviceModulePointer();

   void release();

   void setSpeakerMute(boolean var1);

   void setMicrophoneMute(boolean var1);
}
