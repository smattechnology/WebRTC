package com.smat.webrtc;

public final class CryptoOptions {
   private final Srtp srtp;
   private final SFrame sframe;

   private CryptoOptions(boolean enableGcmCryptoSuites, boolean enableAes128Sha1_32CryptoCipher, boolean enableEncryptedRtpHeaderExtensions, boolean requireFrameEncryption) {
      this.srtp = new Srtp(enableGcmCryptoSuites, enableAes128Sha1_32CryptoCipher, enableEncryptedRtpHeaderExtensions);
      this.sframe = new SFrame(requireFrameEncryption);
   }

   public static Builder builder() {
      return new Builder();
   }

   @CalledByNative
   public Srtp getSrtp() {
      return this.srtp;
   }

   @CalledByNative
   public SFrame getSFrame() {
      return this.sframe;
   }

   // $FF: synthetic method
   CryptoOptions(boolean x0, boolean x1, boolean x2, boolean x3, Object x4) {
      this(x0, x1, x2, x3);
   }

   public static class Builder {
      private boolean enableGcmCryptoSuites;
      private boolean enableAes128Sha1_32CryptoCipher;
      private boolean enableEncryptedRtpHeaderExtensions;
      private boolean requireFrameEncryption;

      private Builder() {
      }

      public Builder setEnableGcmCryptoSuites(boolean enableGcmCryptoSuites) {
         this.enableGcmCryptoSuites = enableGcmCryptoSuites;
         return this;
      }

      public Builder setEnableAes128Sha1_32CryptoCipher(boolean enableAes128Sha1_32CryptoCipher) {
         this.enableAes128Sha1_32CryptoCipher = enableAes128Sha1_32CryptoCipher;
         return this;
      }

      public Builder setEnableEncryptedRtpHeaderExtensions(boolean enableEncryptedRtpHeaderExtensions) {
         this.enableEncryptedRtpHeaderExtensions = enableEncryptedRtpHeaderExtensions;
         return this;
      }

      public Builder setRequireFrameEncryption(boolean requireFrameEncryption) {
         this.requireFrameEncryption = requireFrameEncryption;
         return this;
      }

      public CryptoOptions createCryptoOptions() {
         return new CryptoOptions(this.enableGcmCryptoSuites, this.enableAes128Sha1_32CryptoCipher, this.enableEncryptedRtpHeaderExtensions, this.requireFrameEncryption);
      }

      // $FF: synthetic method
      Builder(Object x0) {
         this();
      }
   }

   public final class SFrame {
      private final boolean requireFrameEncryption;

      private SFrame(boolean requireFrameEncryption) {
         this.requireFrameEncryption = requireFrameEncryption;
      }

      @CalledByNative("SFrame")
      public boolean getRequireFrameEncryption() {
         return this.requireFrameEncryption;
      }

      // $FF: synthetic method
      SFrame(boolean x1, Object x2) {
         this(x1);
      }
   }

   public final class Srtp {
      private final boolean enableGcmCryptoSuites;
      private final boolean enableAes128Sha1_32CryptoCipher;
      private final boolean enableEncryptedRtpHeaderExtensions;

      private Srtp(boolean enableGcmCryptoSuites, boolean enableAes128Sha1_32CryptoCipher, boolean enableEncryptedRtpHeaderExtensions) {
         this.enableGcmCryptoSuites = enableGcmCryptoSuites;
         this.enableAes128Sha1_32CryptoCipher = enableAes128Sha1_32CryptoCipher;
         this.enableEncryptedRtpHeaderExtensions = enableEncryptedRtpHeaderExtensions;
      }

      @CalledByNative("Srtp")
      public boolean getEnableGcmCryptoSuites() {
         return this.enableGcmCryptoSuites;
      }

      @CalledByNative("Srtp")
      public boolean getEnableAes128Sha1_32CryptoCipher() {
         return this.enableAes128Sha1_32CryptoCipher;
      }

      @CalledByNative("Srtp")
      public boolean getEnableEncryptedRtpHeaderExtensions() {
         return this.enableEncryptedRtpHeaderExtensions;
      }

      // $FF: synthetic method
      Srtp(boolean x1, boolean x2, boolean x3, Object x4) {
         this(x1, x2, x3);
      }
   }
}
