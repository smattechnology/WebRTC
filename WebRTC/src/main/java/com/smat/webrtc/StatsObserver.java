package com.smat.webrtc;
/* loaded from: input.aar:classes.jar:org/webrtc/StatsObserver.class */
public interface StatsObserver {
    @CalledByNative
    void onComplete(StatsReport[] statsReportArr);
}
