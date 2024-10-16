package com.smat.webrtc;

import androidx.annotation.Nullable;
import java.util.List;
import java.util.Map;

public class RtpParameters {
   public final String transactionId;
   private final Rtcp rtcp;
   private final List<HeaderExtension> headerExtensions;
   public final List<Encoding> encodings;
   public final List<Codec> codecs;

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

   public static class Encoding {
      @Nullable
      public String rid;
      public boolean active = true;
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
         this.rid = rid;
         this.active = active;
         this.scaleResolutionDownBy = scaleResolutionDownBy;
      }

      @CalledByNative("Encoding")
      Encoding(String rid, boolean active, Integer maxBitrateBps, Integer minBitrateBps, Integer maxFramerate, Integer numTemporalLayers, Double scaleResolutionDownBy, Long ssrc) {
         this.rid = rid;
         this.active = active;
         this.maxBitrateBps = maxBitrateBps;
         this.minBitrateBps = minBitrateBps;
         this.maxFramerate = maxFramerate;
         this.numTemporalLayers = numTemporalLayers;
         this.scaleResolutionDownBy = scaleResolutionDownBy;
         this.ssrc = ssrc;
      }

      @Nullable
      @CalledByNative("Encoding")
      String getRid() {
         return this.rid;
      }

      @CalledByNative("Encoding")
      boolean getActive() {
         return this.active;
      }

      @Nullable
      @CalledByNative("Encoding")
      Integer getMaxBitrateBps() {
         return this.maxBitrateBps;
      }

      @Nullable
      @CalledByNative("Encoding")
      Integer getMinBitrateBps() {
         return this.minBitrateBps;
      }

      @Nullable
      @CalledByNative("Encoding")
      Integer getMaxFramerate() {
         return this.maxFramerate;
      }

      @Nullable
      @CalledByNative("Encoding")
      Integer getNumTemporalLayers() {
         return this.numTemporalLayers;
      }

      @Nullable
      @CalledByNative("Encoding")
      Double getScaleResolutionDownBy() {
         return this.scaleResolutionDownBy;
      }

      @CalledByNative("Encoding")
      Long getSsrc() {
         return this.ssrc;
      }
   }
}
