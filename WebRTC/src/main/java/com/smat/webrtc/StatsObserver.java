package com.smat.webrtc;

public interface StatsObserver {
   @CalledByNative
   void onComplete(StatsReport[] var1);
}
