package com.smat.webrtc.audio;
/* loaded from: input.aar:classes.jar:org/webrtc/audio/AudioDeviceModule.class */
public interface AudioDeviceModule {
    long getNativeAudioDeviceModulePointer();

    void release();

    void setSpeakerMute(boolean z);

    void setMicrophoneMute(boolean z);
}
