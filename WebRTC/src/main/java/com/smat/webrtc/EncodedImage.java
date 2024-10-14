package com.smat.webrtc;

import androidx.annotation.Nullable;
import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;

public class EncodedImage implements RefCounted {
   private final RefCountDelegate refCountDelegate;
   private final boolean supportsRetain;
   public final ByteBuffer buffer;
   public final int encodedWidth;
   public final int encodedHeight;
   public final long captureTimeMs;
   public final long captureTimeNs;
   public final FrameType frameType;
   public final int rotation;
   public final boolean completeFrame;
   @Nullable
   public final Integer qp;

   public void retain() {
      this.refCountDelegate.retain();
   }

   public void release() {
      this.refCountDelegate.release();
   }

   @CalledByNative
   boolean maybeRetain() {
      if (this.supportsRetain) {
         this.retain();
         return true;
      } else {
         return false;
      }
   }

   @CalledByNative
   private EncodedImage(ByteBuffer buffer, boolean supportsRetain, @Nullable Runnable releaseCallback, int encodedWidth, int encodedHeight, long captureTimeNs, FrameType frameType, int rotation, boolean completeFrame, @Nullable Integer qp) {
      this.buffer = buffer;
      this.encodedWidth = encodedWidth;
      this.encodedHeight = encodedHeight;
      this.captureTimeMs = TimeUnit.NANOSECONDS.toMillis(captureTimeNs);
      this.captureTimeNs = captureTimeNs;
      this.frameType = frameType;
      this.rotation = rotation;
      this.completeFrame = completeFrame;
      this.qp = qp;
      this.supportsRetain = supportsRetain;
      this.refCountDelegate = new RefCountDelegate(releaseCallback);
   }

   @CalledByNative
   private ByteBuffer getBuffer() {
      return this.buffer;
   }

   @CalledByNative
   private int getEncodedWidth() {
      return this.encodedWidth;
   }

   @CalledByNative
   private int getEncodedHeight() {
      return this.encodedHeight;
   }

   @CalledByNative
   private long getCaptureTimeNs() {
      return this.captureTimeNs;
   }

   @CalledByNative
   private int getFrameType() {
      return this.frameType.getNative();
   }

   @CalledByNative
   private int getRotation() {
      return this.rotation;
   }

   @CalledByNative
   private boolean getCompleteFrame() {
      return this.completeFrame;
   }

   @CalledByNative
   @Nullable
   private Integer getQp() {
      return this.qp;
   }

   public static Builder builder() {
      return new Builder();
   }

   // $FF: synthetic method
   EncodedImage(ByteBuffer x0, boolean x1, Runnable x2, int x3, int x4, long x5, FrameType x6, int x7, boolean x8, Integer x9, Object x10) {
      this(x0, x1, x2, x3, x4, x5, x6, x7, x8, x9);
   }

   public static class Builder {
      private ByteBuffer buffer;
      private boolean supportsRetain;
      @Nullable
      private Runnable releaseCallback;
      private int encodedWidth;
      private int encodedHeight;
      private long captureTimeNs;
      private FrameType frameType;
      private int rotation;
      private boolean completeFrame;
      @Nullable
      private Integer qp;

      private Builder() {
      }

      /** @deprecated */
      @Deprecated
      public Builder setBuffer(ByteBuffer buffer) {
         this.buffer = buffer;
         this.releaseCallback = null;
         this.supportsRetain = false;
         return this;
      }

      public Builder setBuffer(ByteBuffer buffer, @Nullable Runnable releaseCallback) {
         this.buffer = buffer;
         this.releaseCallback = releaseCallback;
         this.supportsRetain = true;
         return this;
      }

      public Builder setEncodedWidth(int encodedWidth) {
         this.encodedWidth = encodedWidth;
         return this;
      }

      public Builder setEncodedHeight(int encodedHeight) {
         this.encodedHeight = encodedHeight;
         return this;
      }

      /** @deprecated */
      @Deprecated
      public Builder setCaptureTimeMs(long captureTimeMs) {
         this.captureTimeNs = TimeUnit.MILLISECONDS.toNanos(captureTimeMs);
         return this;
      }

      public Builder setCaptureTimeNs(long captureTimeNs) {
         this.captureTimeNs = captureTimeNs;
         return this;
      }

      public Builder setFrameType(FrameType frameType) {
         this.frameType = frameType;
         return this;
      }

      public Builder setRotation(int rotation) {
         this.rotation = rotation;
         return this;
      }

      public Builder setCompleteFrame(boolean completeFrame) {
         this.completeFrame = completeFrame;
         return this;
      }

      public Builder setQp(@Nullable Integer qp) {
         this.qp = qp;
         return this;
      }

      public EncodedImage createEncodedImage() {
         return new EncodedImage(this.buffer, this.supportsRetain, this.releaseCallback, this.encodedWidth, this.encodedHeight, this.captureTimeNs, this.frameType, this.rotation, this.completeFrame, this.qp);
      }

      // $FF: synthetic method
      Builder(Object x0) {
         this();
      }
   }

   public static enum FrameType {
      EmptyFrame(0),
      VideoFrameKey(3),
      VideoFrameDelta(4);

      private final int nativeIndex;

      private FrameType(int nativeIndex) {
         this.nativeIndex = nativeIndex;
      }

      public int getNative() {
         return this.nativeIndex;
      }

      @CalledByNative("FrameType")
      static FrameType fromNativeIndex(int nativeIndex) {
         FrameType[] var1 = values();
         int var2 = var1.length;

         for(int var3 = 0; var3 < var2; ++var3) {
            FrameType type = var1[var3];
            if (type.getNative() == nativeIndex) {
               return type;
            }
         }

         throw new IllegalArgumentException("Unknown native frame type: " + nativeIndex);
      }
   }
}
