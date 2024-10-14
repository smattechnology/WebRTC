package com.smat.webrtc;

public interface RTCStatsCollectorCallback {
   @CalledByNative
   void onStatsDelivered(RTCStatsReport var1);
}
