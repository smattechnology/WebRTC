package com.smat.webrtc;

import android.support.annotation.Nullable;
import org.webrtc.PeerConnection;
/* loaded from: input.aar:classes.jar:org/webrtc/PeerConnectionDependencies.class */
public final class PeerConnectionDependencies {
    private final PeerConnection.Observer observer;
    private final SSLCertificateVerifier sslCertificateVerifier;

    /* loaded from: input.aar:classes.jar:org/webrtc/PeerConnectionDependencies$Builder.class */
    public static class Builder {
        private PeerConnection.Observer observer;
        private SSLCertificateVerifier sslCertificateVerifier;

        private Builder(PeerConnection.Observer observer) {
            this.observer = observer;
        }

        public Builder setSSLCertificateVerifier(SSLCertificateVerifier sslCertificateVerifier) {
            this.sslCertificateVerifier = sslCertificateVerifier;
            return this;
        }

        public PeerConnectionDependencies createPeerConnectionDependencies() {
            return new PeerConnectionDependencies(this.observer, this.sslCertificateVerifier);
        }
    }

    public static Builder builder(PeerConnection.Observer observer) {
        return new Builder(observer);
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public PeerConnection.Observer getObserver() {
        return this.observer;
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    @Nullable
    public SSLCertificateVerifier getSSLCertificateVerifier() {
        return this.sslCertificateVerifier;
    }

    private PeerConnectionDependencies(PeerConnection.Observer observer, SSLCertificateVerifier sslCertificateVerifier) {
        this.observer = observer;
        this.sslCertificateVerifier = sslCertificateVerifier;
    }
}
