package com.smat.webrtc;
/* loaded from: input.aar:classes.jar:org/webrtc/VideoCodecType.class */
enum VideoCodecType {
    VP8("video/x-vnd.on2.vp8"),
    VP9("video/x-vnd.on2.vp9"),
    H264("video/avc");
    
    private final String mimeType;

    VideoCodecType(String mimeType) {
        this.mimeType = mimeType;
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public String mimeType() {
        return this.mimeType;
    }
}
