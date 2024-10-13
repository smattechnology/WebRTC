package com.smat.webrtc;

import android.support.annotation.Nullable;
import java.util.List;
import java.util.Map;
import org.webrtc.MediaStreamTrack;
/* loaded from: input.aar:classes.jar:org/webrtc/RtpParameters.class */
public class RtpParameters {
    public final String transactionId;
    private final Rtcp rtcp;
    private final List<HeaderExtension> headerExtensions;
    public final List<Encoding> encodings;
    public final List<Codec> codecs;

    /* loaded from: input.aar:classes.jar:org/webrtc/RtpParameters$Encoding.class */
    public static class Encoding {
        @Nullable
        public String rid;
        public boolean active;
        @Nullable
        public Integer maxBitrateBps;
        @Nullable
        public Integer minBitrateBps;
        @Nullable
        public Integer maxFramerate;
        @Nullable
        public Integer numTemporalLayers;
        @Nullable
        public Double scaleResolutionDownBy;
        public Long ssrc;

        public Encoding(String rid, boolean active, Double scaleResolutionDownBy) {
            this.active = true;
            this.rid = rid;
            this.active = active;
            this.scaleResolutionDownBy = scaleResolutionDownBy;
        }

        @CalledByNative("Encoding")
        Encoding(String rid, boolean active, Integer maxBitrateBps, Integer minBitrateBps, Integer maxFramerate, Integer numTemporalLayers, Double scaleResolutionDownBy, Long ssrc) {
            this.active = true;
            this.rid = rid;
            this.active = active;
            this.maxBitrateBps = maxBitrateBps;
            this.minBitrateBps = minBitrateBps;
            this.maxFramerate = maxFramerate;
            this.numTemporalLayers = numTemporalLayers;
            this.scaleResolutionDownBy = scaleResolutionDownBy;
            this.ssrc = ssrc;
        }

        @CalledByNative("Encoding")
        @Nullable
        String getRid() {
            return this.rid;
        }

        @CalledByNative("Encoding")
        boolean getActive() {
            return this.active;
        }

        @CalledByNative("Encoding")
        @Nullable
        Integer getMaxBitrateBps() {
            return this.maxBitrateBps;
        }

        @CalledByNative("Encoding")
        @Nullable
        Integer getMinBitrateBps() {
            return this.minBitrateBps;
        }

        @CalledByNative("Encoding")
        @Nullable
        Integer getMaxFramerate() {
            return this.maxFramerate;
        }

        @CalledByNative("Encoding")
        @Nullable
        Integer getNumTemporalLayers() {
            return this.numTemporalLayers;
        }

        @CalledByNative("Encoding")
        @Nullable
        Double getScaleResolutionDownBy() {
            return this.scaleResolutionDownBy;
        }

        @CalledByNative("Encoding")
        Long getSsrc() {
            return this.ssrc;
        }
    }

    /* loaded from: input.aar:classes.jar:org/webrtc/RtpParameters$Codec.class */
    public static class Codec {
        public int payloadType;
        public String name;
        MediaStreamTrack.MediaType kind;
        public Integer clockRate;
        public Integer numChannels;
        public Map<String, String> parameters;

        @CalledByNative("Codec")
        Codec(int payloadType, String name, MediaStreamTrack.MediaType kind, Integer clockRate, Integer numChannels, Map<String, String> parameters) {
            this.payloadType = payloadType;
            this.name = name;
            this.kind = kind;
            this.clockRate = clockRate;
            this.numChannels = numChannels;
            this.parameters = parameters;
        }

        @CalledByNative("Codec")
        int getPayloadType() {
            return this.payloadType;
        }

        @CalledByNative("Codec")
        String getName() {
            return this.name;
        }

        @CalledByNative("Codec")
        MediaStreamTrack.MediaType getKind() {
            return this.kind;
        }

        @CalledByNative("Codec")
        Integer getClockRate() {
            return this.clockRate;
        }

        @CalledByNative("Codec")
        Integer getNumChannels() {
            return this.numChannels;
        }

        @CalledByNative("Codec")
        Map getParameters() {
            return this.parameters;
        }
    }

    /* loaded from: input.aar:classes.jar:org/webrtc/RtpParameters$Rtcp.class */
    public static class Rtcp {
        private final String cname;
        private final boolean reducedSize;

        @CalledByNative("Rtcp")
        Rtcp(String cname, boolean reducedSize) {
            this.cname = cname;
            this.reducedSize = reducedSize;
        }

        @CalledByNative("Rtcp")
        public String getCname() {
            return this.cname;
        }

        @CalledByNative("Rtcp")
        public boolean getReducedSize() {
            return this.reducedSize;
        }
    }

    /* loaded from: input.aar:classes.jar:org/webrtc/RtpParameters$HeaderExtension.class */
    public static class HeaderExtension {
        private final String uri;
        private final int id;
        private final boolean encrypted;

        @CalledByNative("HeaderExtension")
        HeaderExtension(String uri, int id, boolean encrypted) {
            this.uri = uri;
            this.id = id;
            this.encrypted = encrypted;
        }

        @CalledByNative("HeaderExtension")
        public String getUri() {
            return this.uri;
        }

        @CalledByNative("HeaderExtension")
        public int getId() {
            return this.id;
        }

        @CalledByNative("HeaderExtension")
        public boolean getEncrypted() {
            return this.encrypted;
        }
    }

    @CalledByNative
    RtpParameters(String transactionId, Rtcp rtcp, List<HeaderExtension> headerExtensions, List<Encoding> encodings, List<Codec> codecs) {
        this.transactionId = transactionId;
        this.rtcp = rtcp;
        this.headerExtensions = headerExtensions;
        this.encodings = encodings;
        this.codecs = codecs;
    }

    @CalledByNative
    String getTransactionId() {
        return this.transactionId;
    }

    @CalledByNative
    public Rtcp getRtcp() {
        return this.rtcp;
    }

    @CalledByNative
    public List<HeaderExtension> getHeaderExtensions() {
        return this.headerExtensions;
    }

    @CalledByNative
    List<Encoding> getEncodings() {
        return this.encodings;
    }

    @CalledByNative
    List<Codec> getCodecs() {
        return this.codecs;
    }
}
