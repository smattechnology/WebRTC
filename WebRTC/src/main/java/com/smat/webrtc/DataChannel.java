package com.smat.webrtc;

import java.nio.ByteBuffer;

public class DataChannel {
   private long nativeDataChannel;
   private long nativeObserver;

   @CalledByNative
   public DataChannel(long nativeDataChannel) {
      this.nativeDataChannel = nativeDataChannel;
   }

   public void registerObserver(Observer observer) {
      this.checkDataChannelExists();
      if (this.nativeObserver != 0L) {
         this.nativeUnregisterObserver(this.nativeObserver);
      }

      this.nativeObserver = this.nativeRegisterObserver(observer);
   }

   public void unregisterObserver() {
      this.checkDataChannelExists();
      this.nativeUnregisterObserver(this.nativeObserver);
   }

   public String label() {
      this.checkDataChannelExists();
      return this.nativeLabel();
   }

   public int id() {
      this.checkDataChannelExists();
      return this.nativeId();
   }

   public State state() {
      this.checkDataChannelExists();
      return this.nativeState();
   }

   public long bufferedAmount() {
      this.checkDataChannelExists();
      return this.nativeBufferedAmount();
   }

   public void close() {
      this.checkDataChannelExists();
      this.nativeClose();
   }

   public boolean send(Buffer buffer) {
      this.checkDataChannelExists();
      byte[] data = new byte[buffer.data.remaining()];
      buffer.data.get(data);
      return this.nativeSend(data, buffer.binary);
   }

   public void dispose() {
      this.checkDataChannelExists();
      JniCommon.nativeReleaseRef(this.nativeDataChannel);
      this.nativeDataChannel = 0L;
   }

   @CalledByNative
   long getNativeDataChannel() {
      return this.nativeDataChannel;
   }

   private void checkDataChannelExists() {
      if (this.nativeDataChannel == 0L) {
         throw new IllegalStateException("DataChannel has been disposed.");
      }
   }

   private native long nativeRegisterObserver(Observer var1);

   private native void nativeUnregisterObserver(long var1);

   private native String nativeLabel();

   private native int nativeId();

   private native State nativeState();

   private native long nativeBufferedAmount();

   private native void nativeClose();

   private native boolean nativeSend(byte[] var1, boolean var2);

   public static enum State {
      CONNECTING,
      OPEN,
      CLOSING,
      CLOSED;

      @CalledByNative("State")
      static State fromNativeIndex(int nativeIndex) {
         return values()[nativeIndex];
      }
   }

   public interface Observer {
      @CalledByNative("Observer")
      void onBufferedAmountChange(long var1);

      @CalledByNative("Observer")
      void onStateChange();

      @CalledByNative("Observer")
      void onMessage(Buffer var1);
   }

   public static class Buffer {
      public final ByteBuffer data;
      public final boolean binary;

      @CalledByNative("Buffer")
      public Buffer(ByteBuffer data, boolean binary) {
         this.data = data;
         this.binary = binary;
      }
   }

   public static class Init {
      public boolean ordered = true;
      public int maxRetransmitTimeMs = -1;
      public int maxRetransmits = -1;
      public String protocol = "";
      public boolean negotiated;
      public int id = -1;

      @CalledByNative("Init")
      boolean getOrdered() {
         return this.ordered;
      }

      @CalledByNative("Init")
      int getMaxRetransmitTimeMs() {
         return this.maxRetransmitTimeMs;
      }

      @CalledByNative("Init")
      int getMaxRetransmits() {
         return this.maxRetransmits;
      }

      @CalledByNative("Init")
      String getProtocol() {
         return this.protocol;
      }

      @CalledByNative("Init")
      boolean getNegotiated() {
         return this.negotiated;
      }

      @CalledByNative("Init")
      int getId() {
         return this.id;
      }
   }
}
