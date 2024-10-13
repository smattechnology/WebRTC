package com.smat.webrtc;

import org.webrtc.VideoDecoder;
/* loaded from: input.aar:classes.jar:org/webrtc/VideoDecoderWrapper.class */
class VideoDecoderWrapper {
    /* JADX INFO: Access modifiers changed from: private */
    public static native void nativeOnDecodedFrame(long j, VideoFrame videoFrame, Integer num, Integer num2);

    VideoDecoderWrapper() {
    }

    @CalledByNative
    static VideoDecoder.Callback createDecoderCallback(long nativeDecoder) {
        return frame, decodeTimeMs, qp -> {
            nativeOnDecodedFrame(nativeDecoder, frame, decodeTimeMs, qp);
        };
    }
}
