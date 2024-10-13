package com.smat.webrtc;
/* loaded from: input.aar:classes.jar:org/webrtc/RTCStatsCollectorCallback.class */
public interface RTCStatsCollectorCallback {
    @CalledByNative
    void onStatsDelivered(RTCStatsReport rTCStatsReport);
}
