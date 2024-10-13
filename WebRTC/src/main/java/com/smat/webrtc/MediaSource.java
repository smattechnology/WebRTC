package com.smat.webrtc;
/* loaded from: input.aar:classes.jar:org/webrtc/MediaSource.class */
public class MediaSource {
    private long nativeSource;

    private static native State nativeGetState(long j);

    /* loaded from: input.aar:classes.jar:org/webrtc/MediaSource$State.class */
    public enum State {
        INITIALIZING,
        LIVE,
        ENDED,
        MUTED;

        @CalledByNative("State")
        static State fromNativeIndex(int nativeIndex) {
            return values()[nativeIndex];
        }
    }

    public MediaSource(long nativeSource) {
        this.nativeSource = nativeSource;
    }

    public State state() {
        checkMediaSourceExists();
        return nativeGetState(this.nativeSource);
    }

    public void dispose() {
        checkMediaSourceExists();
        JniCommon.nativeReleaseRef(this.nativeSource);
        this.nativeSource = 0L;
    }

    /* JADX INFO: Access modifiers changed from: protected */
    public long getNativeMediaSource() {
        checkMediaSourceExists();
        return this.nativeSource;
    }

    private void checkMediaSourceExists() {
        if (this.nativeSource == 0) {
            throw new IllegalStateException("MediaSource has been disposed.");
        }
    }
}
