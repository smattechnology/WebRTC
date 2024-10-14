package com.smat.webrtc;

public interface RefCounted {
   void retain();

   @CalledByNative
   void release();
}
