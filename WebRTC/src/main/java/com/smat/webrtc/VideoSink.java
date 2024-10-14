package com.smat.webrtc;

public interface VideoSink {
   @CalledByNative
   void onFrame(VideoFrame var1);
}
