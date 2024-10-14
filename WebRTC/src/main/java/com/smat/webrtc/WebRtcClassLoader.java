package com.smat.webrtc;

class WebRtcClassLoader {
   @CalledByNative
   static Object getClassLoader() {
      Object loader = WebRtcClassLoader.class.getClassLoader();
      if (loader == null) {
         throw new RuntimeException("Failed to get WebRTC class loader.");
      } else {
         return loader;
      }
   }
}
