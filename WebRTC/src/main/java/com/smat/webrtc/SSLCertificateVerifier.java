package com.smat.webrtc;
/* loaded from: input.aar:classes.jar:org/webrtc/SSLCertificateVerifier.class */
public interface SSLCertificateVerifier {
    @CalledByNative
    boolean verify(byte[] bArr);
}
