package com.smat.webrtc;

import java.util.IdentityHashMap;
/* loaded from: input.aar:classes.jar:org/webrtc/VideoTrack.class */
public class VideoTrack extends MediaStreamTrack {
    private final IdentityHashMap<VideoSink, Long> sinks;

    private static native void nativeAddSink(long j, long j2);

    private static native void nativeRemoveSink(long j, long j2);

    private static native long nativeWrapSink(VideoSink videoSink);

    private static native void nativeFreeSink(long j);

    public VideoTrack(long nativeTrack) {
        super(nativeTrack);
        this.sinks = new IdentityHashMap<>();
    }

    public void addSink(VideoSink sink) {
        if (sink == null) {
            throw new IllegalArgumentException("The VideoSink is not allowed to be null");
        }
        if (!this.sinks.containsKey(sink)) {
            long nativeSink = nativeWrapSink(sink);
            this.sinks.put(sink, Long.valueOf(nativeSink));
            nativeAddSink(getNativeMediaStreamTrack(), nativeSink);
        }
    }

    public void removeSink(VideoSink sink) {
        Long nativeSink = this.sinks.remove(sink);
        if (nativeSink != null) {
            nativeRemoveSink(getNativeMediaStreamTrack(), nativeSink.longValue());
            nativeFreeSink(nativeSink.longValue());
        }
    }

    @Override // org.webrtc.MediaStreamTrack
    public void dispose() {
        for (Long l : this.sinks.values()) {
            long nativeSink = l.longValue();
            nativeRemoveSink(getNativeMediaStreamTrack(), nativeSink);
            nativeFreeSink(nativeSink);
        }
        this.sinks.clear();
        super.dispose();
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public long getNativeVideoTrack() {
        return getNativeMediaStreamTrack();
    }
}
