package com.smat.webrtc;

import android.support.annotation.Nullable;
/* loaded from: input.aar:classes.jar:org/webrtc/VideoEncoderFactory.class */
public interface VideoEncoderFactory {
    @CalledByNative
    @Nullable
    VideoEncoder createEncoder(VideoCodecInfo videoCodecInfo);

    @CalledByNative
    VideoCodecInfo[] getSupportedCodecs();

    @CalledByNative
    default VideoCodecInfo[] getImplementations() {
        return getSupportedCodecs();
    }
}
