package com.smat.webrtc;

import java.nio.ByteBuffer;
/* loaded from: input.aar:classes.jar:org/webrtc/JniCommon.class */
public class JniCommon {
    public static native void nativeAddRef(long j);

    public static native void nativeReleaseRef(long j);

    public static native ByteBuffer nativeAllocateByteBuffer(int i);

    public static native void nativeFreeByteBuffer(ByteBuffer byteBuffer);
}
