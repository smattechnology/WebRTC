package com.smat.webrtc;
/* loaded from: input.aar:classes.jar:org/webrtc/WebRtcClassLoader.class */
class WebRtcClassLoader {
    WebRtcClassLoader() {
    }

    @CalledByNative
    static Object getClassLoader() {
        Object loader = WebRtcClassLoader.class.getClassLoader();
        if (loader == null) {
            throw new RuntimeException("Failed to get WebRTC class loader.");
        }
        return loader;
    }
}
