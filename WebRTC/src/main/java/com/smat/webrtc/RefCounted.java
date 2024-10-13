package com.smat.webrtc;
/* loaded from: input.aar:classes.jar:org/webrtc/RefCounted.class */
public interface RefCounted {
    void retain();

    @CalledByNative
    void release();
}
