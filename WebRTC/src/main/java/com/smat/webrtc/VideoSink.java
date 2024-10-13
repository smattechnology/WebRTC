package com.smat.webrtc;
/* loaded from: input.aar:classes.jar:org/webrtc/VideoSink.class */
public interface VideoSink {
    @CalledByNative
    void onFrame(VideoFrame videoFrame);
}
