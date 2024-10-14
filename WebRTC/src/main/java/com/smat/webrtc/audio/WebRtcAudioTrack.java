package com.smat.webrtc.audio;

import android.annotation.TargetApi;
import android.content.Context;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.AudioAttributes.Builder;
import android.os.Process;
import android.os.Build.VERSION;
import androidx.annotation.Nullable;
import java.nio.ByteBuffer;
import org.webrtc.CalledByNative;
import org.webrtc.Logging;
import org.webrtc.ThreadUtils;

class WebRtcAudioTrack {
   private static final String TAG = "WebRtcAudioTrackExternal";
   private static final int BITS_PER_SAMPLE = 16;
   private static final int CALLBACK_BUFFER_SIZE_MS = 10;
   private static final int BUFFERS_PER_SECOND = 100;
   private static final long AUDIO_TRACK_THREAD_JOIN_TIMEOUT_MS = 2000L;
   private static final int DEFAULT_USAGE = getDefaultUsageAttribute();
   private static final int AUDIO_TRACK_START = 0;
   private static final int AUDIO_TRACK_STOP = 1;
   private long nativeAudioTrack;
   private final Context context;
   private final AudioManager audioManager;
   private final ThreadUtils.ThreadChecker threadChecker;
   private ByteBuffer byteBuffer;
   @Nullable
   private AudioTrack audioTrack;
   @Nullable
   private WebRtcAudioTrack.AudioTrackThread audioThread;
   private final VolumeLogger volumeLogger;
   private volatile boolean speakerMute;
   private byte[] emptyBytes;
   @Nullable
   private final JavaAudioDeviceModule.AudioTrackErrorCallback errorCallback;
   @Nullable
   private final JavaAudioDeviceModule.AudioTrackStateCallback stateCallback;

   private static int getDefaultUsageAttribute() {
      return VERSION.SDK_INT >= 21 ? 2 : 0;
   }

   @CalledByNative
   WebRtcAudioTrack(Context context, AudioManager audioManager) {
      this(context, audioManager, (JavaAudioDeviceModule.AudioTrackErrorCallback)null, (JavaAudioDeviceModule.AudioTrackStateCallback)null);
   }

   WebRtcAudioTrack(Context context, AudioManager audioManager, @Nullable JavaAudioDeviceModule.AudioTrackErrorCallback errorCallback, @Nullable JavaAudioDeviceModule.AudioTrackStateCallback stateCallback) {
      this.threadChecker = new ThreadUtils.ThreadChecker();
      this.threadChecker.detachThread();
      this.context = context;
      this.audioManager = audioManager;
      this.errorCallback = errorCallback;
      this.stateCallback = stateCallback;
      this.volumeLogger = new VolumeLogger(audioManager);
      Logging.d("WebRtcAudioTrackExternal", "ctor" + WebRtcAudioUtils.getThreadInfo());
   }

   @CalledByNative
   public void setNativeAudioTrack(long nativeAudioTrack) {
      this.nativeAudioTrack = nativeAudioTrack;
   }

   @CalledByNative
   private boolean initPlayout(int sampleRate, int channels, double bufferSizeFactor) {
      this.threadChecker.checkIsOnValidThread();
      Logging.d("WebRtcAudioTrackExternal", "initPlayout(sampleRate=" + sampleRate + ", channels=" + channels + ", bufferSizeFactor=" + bufferSizeFactor + ")");
      int bytesPerFrame = channels * 2;
      this.byteBuffer = ByteBuffer.allocateDirect(bytesPerFrame * (sampleRate / 100));
      Logging.d("WebRtcAudioTrackExternal", "byteBuffer.capacity: " + this.byteBuffer.capacity());
      this.emptyBytes = new byte[this.byteBuffer.capacity()];
      nativeCacheDirectBufferAddress(this.nativeAudioTrack, this.byteBuffer);
      int channelConfig = this.channelCountToConfiguration(channels);
      int minBufferSizeInBytes = (int)((double)AudioTrack.getMinBufferSize(sampleRate, channelConfig, 2) * bufferSizeFactor);
      Logging.d("WebRtcAudioTrackExternal", "minBufferSizeInBytes: " + minBufferSizeInBytes);
      if (minBufferSizeInBytes < this.byteBuffer.capacity()) {
         this.reportWebRtcAudioTrackInitError("AudioTrack.getMinBufferSize returns an invalid value.");
         return false;
      } else if (this.audioTrack != null) {
         this.reportWebRtcAudioTrackInitError("Conflict with existing AudioTrack.");
         return false;
      } else {
         try {
            if (VERSION.SDK_INT >= 21) {
               this.audioTrack = createAudioTrackOnLollipopOrHigher(sampleRate, channelConfig, minBufferSizeInBytes);
            } else {
               this.audioTrack = createAudioTrackOnLowerThanLollipop(sampleRate, channelConfig, minBufferSizeInBytes);
            }
         } catch (IllegalArgumentException var9) {
            this.reportWebRtcAudioTrackInitError(var9.getMessage());
            this.releaseAudioResources();
            return false;
         }

         if (this.audioTrack != null && this.audioTrack.getState() == 1) {
            this.logMainParameters();
            this.logMainParametersExtended();
            return true;
         } else {
            this.reportWebRtcAudioTrackInitError("Initialization of audio track failed.");
            this.releaseAudioResources();
            return false;
         }
      }
   }

   @CalledByNative
   private boolean startPlayout() {
      this.threadChecker.checkIsOnValidThread();
      this.volumeLogger.start();
      Logging.d("WebRtcAudioTrackExternal", "startPlayout");
      assertTrue(this.audioTrack != null);
      assertTrue(this.audioThread == null);

      try {
         this.audioTrack.play();
      } catch (IllegalStateException var2) {
         this.reportWebRtcAudioTrackStartError(JavaAudioDeviceModule.AudioTrackStartErrorCode.AUDIO_TRACK_START_EXCEPTION, "AudioTrack.play failed: " + var2.getMessage());
         this.releaseAudioResources();
         return false;
      }

      if (this.audioTrack.getPlayState() != 3) {
         this.reportWebRtcAudioTrackStartError(JavaAudioDeviceModule.AudioTrackStartErrorCode.AUDIO_TRACK_START_STATE_MISMATCH, "AudioTrack.play failed - incorrect state :" + this.audioTrack.getPlayState());
         this.releaseAudioResources();
         return false;
      } else {
         this.audioThread = new AudioTrackThread("AudioTrackJavaThread");
         this.audioThread.start();
         return true;
      }
   }

   @CalledByNative
   private boolean stopPlayout() {
      this.threadChecker.checkIsOnValidThread();
      this.volumeLogger.stop();
      Logging.d("WebRtcAudioTrackExternal", "stopPlayout");
      assertTrue(this.audioThread != null);
      this.logUnderrunCount();
      this.audioThread.stopThread();
      Logging.d("WebRtcAudioTrackExternal", "Stopping the AudioTrackThread...");
      this.audioThread.interrupt();
      if (!ThreadUtils.joinUninterruptibly(this.audioThread, 2000L)) {
         Logging.e("WebRtcAudioTrackExternal", "Join of AudioTrackThread timed out.");
         WebRtcAudioUtils.logAudioState("WebRtcAudioTrackExternal", this.context, this.audioManager);
      }

      Logging.d("WebRtcAudioTrackExternal", "AudioTrackThread has now been stopped.");
      this.audioThread = null;
      this.releaseAudioResources();
      return true;
   }

   @CalledByNative
   private int getStreamMaxVolume() {
      this.threadChecker.checkIsOnValidThread();
      Logging.d("WebRtcAudioTrackExternal", "getStreamMaxVolume");
      return this.audioManager.getStreamMaxVolume(0);
   }

   @CalledByNative
   private boolean setStreamVolume(int volume) {
      this.threadChecker.checkIsOnValidThread();
      Logging.d("WebRtcAudioTrackExternal", "setStreamVolume(" + volume + ")");
      if (this.isVolumeFixed()) {
         Logging.e("WebRtcAudioTrackExternal", "The device implements a fixed volume policy.");
         return false;
      } else {
         this.audioManager.setStreamVolume(0, volume, 0);
         return true;
      }
   }

   private boolean isVolumeFixed() {
      return VERSION.SDK_INT < 21 ? false : this.audioManager.isVolumeFixed();
   }

   @CalledByNative
   private int getStreamVolume() {
      this.threadChecker.checkIsOnValidThread();
      Logging.d("WebRtcAudioTrackExternal", "getStreamVolume");
      return this.audioManager.getStreamVolume(0);
   }

   @CalledByNative
   private int GetPlayoutUnderrunCount() {
      if (VERSION.SDK_INT >= 24) {
         return this.audioTrack != null ? this.audioTrack.getUnderrunCount() : -1;
      } else {
         return -2;
      }
   }

   private void logMainParameters() {
      Logging.d("WebRtcAudioTrackExternal", "AudioTrack: session ID: " + this.audioTrack.getAudioSessionId() + ", channels: " + this.audioTrack.getChannelCount() + ", sample rate: " + this.audioTrack.getSampleRate() + ", max gain: " + AudioTrack.getMaxVolume());
   }

   @TargetApi(21)
   private static AudioTrack createAudioTrackOnLollipopOrHigher(int sampleRateInHz, int channelConfig, int bufferSizeInBytes) {
      Logging.d("WebRtcAudioTrackExternal", "createAudioTrackOnLollipopOrHigher");
      int nativeOutputSampleRate = AudioTrack.getNativeOutputSampleRate(0);
      Logging.d("WebRtcAudioTrackExternal", "nativeOutputSampleRate: " + nativeOutputSampleRate);
      if (sampleRateInHz != nativeOutputSampleRate) {
         Logging.w("WebRtcAudioTrackExternal", "Unable to use fast mode since requested sample rate is not native");
      }

      return new AudioTrack((new Builder()).setUsage(DEFAULT_USAGE).setContentType(1).build(), (new android.media.AudioFormat.Builder()).setEncoding(2).setSampleRate(sampleRateInHz).setChannelMask(channelConfig).build(), bufferSizeInBytes, 1, 0);
   }

   private static AudioTrack createAudioTrackOnLowerThanLollipop(int sampleRateInHz, int channelConfig, int bufferSizeInBytes) {
      return new AudioTrack(0, sampleRateInHz, channelConfig, 2, bufferSizeInBytes, 1);
   }

   private void logBufferSizeInFrames() {
      if (VERSION.SDK_INT >= 23) {
         Logging.d("WebRtcAudioTrackExternal", "AudioTrack: buffer size in frames: " + this.audioTrack.getBufferSizeInFrames());
      }

   }

   private void logBufferCapacityInFrames() {
      if (VERSION.SDK_INT >= 24) {
         Logging.d("WebRtcAudioTrackExternal", "AudioTrack: buffer capacity in frames: " + this.audioTrack.getBufferCapacityInFrames());
      }

   }

   private void logMainParametersExtended() {
      this.logBufferSizeInFrames();
      this.logBufferCapacityInFrames();
   }

   private void logUnderrunCount() {
      if (VERSION.SDK_INT >= 24) {
         Logging.d("WebRtcAudioTrackExternal", "underrun count: " + this.audioTrack.getUnderrunCount());
      }

   }

   private static void assertTrue(boolean condition) {
      if (!condition) {
         throw new AssertionError("Expected condition to be true");
      }
   }

   private int channelCountToConfiguration(int channels) {
      return channels == 1 ? 4 : 12;
   }

   private static native void nativeCacheDirectBufferAddress(long var0, ByteBuffer var2);

   private static native void nativeGetPlayoutData(long var0, int var2);

   public void setSpeakerMute(boolean mute) {
      Logging.w("WebRtcAudioTrackExternal", "setSpeakerMute(" + mute + ")");
      this.speakerMute = mute;
   }

   private void releaseAudioResources() {
      Logging.d("WebRtcAudioTrackExternal", "releaseAudioResources");
      if (this.audioTrack != null) {
         this.audioTrack.release();
         this.audioTrack = null;
      }

   }

   private void reportWebRtcAudioTrackInitError(String errorMessage) {
      Logging.e("WebRtcAudioTrackExternal", "Init playout error: " + errorMessage);
      WebRtcAudioUtils.logAudioState("WebRtcAudioTrackExternal", this.context, this.audioManager);
      if (this.errorCallback != null) {
         this.errorCallback.onWebRtcAudioTrackInitError(errorMessage);
      }

   }

   private void reportWebRtcAudioTrackStartError(JavaAudioDeviceModule.AudioTrackStartErrorCode errorCode, String errorMessage) {
      Logging.e("WebRtcAudioTrackExternal", "Start playout error: " + errorCode + ". " + errorMessage);
      WebRtcAudioUtils.logAudioState("WebRtcAudioTrackExternal", this.context, this.audioManager);
      if (this.errorCallback != null) {
         this.errorCallback.onWebRtcAudioTrackStartError(errorCode, errorMessage);
      }

   }

   private void reportWebRtcAudioTrackError(String errorMessage) {
      Logging.e("WebRtcAudioTrackExternal", "Run-time playback error: " + errorMessage);
      WebRtcAudioUtils.logAudioState("WebRtcAudioTrackExternal", this.context, this.audioManager);
      if (this.errorCallback != null) {
         this.errorCallback.onWebRtcAudioTrackError(errorMessage);
      }

   }

   private void doAudioTrackStateCallback(int audioState) {
      Logging.d("WebRtcAudioTrackExternal", "doAudioTrackStateCallback: " + audioState);
      if (this.stateCallback != null) {
         if (audioState == 0) {
            this.stateCallback.onWebRtcAudioTrackStart();
         } else if (audioState == 1) {
            this.stateCallback.onWebRtcAudioTrackStop();
         } else {
            Logging.e("WebRtcAudioTrackExternal", "Invalid audio state");
         }
      }

   }

   private class AudioTrackThread extends Thread {
      private volatile boolean keepAlive = true;

      public AudioTrackThread(String name) {
         super(name);
      }

      public void run() {
         Process.setThreadPriority(-19);
         Logging.d("WebRtcAudioTrackExternal", "AudioTrackThread" + WebRtcAudioUtils.getThreadInfo());
         WebRtcAudioTrack.assertTrue(WebRtcAudioTrack.this.audioTrack.getPlayState() == 3);
         WebRtcAudioTrack.this.doAudioTrackStateCallback(0);

         for(int sizeInBytes = WebRtcAudioTrack.this.byteBuffer.capacity(); this.keepAlive; WebRtcAudioTrack.this.byteBuffer.rewind()) {
            WebRtcAudioTrack.nativeGetPlayoutData(WebRtcAudioTrack.this.nativeAudioTrack, sizeInBytes);
            WebRtcAudioTrack.assertTrue(sizeInBytes <= WebRtcAudioTrack.this.byteBuffer.remaining());
            if (WebRtcAudioTrack.this.speakerMute) {
               WebRtcAudioTrack.this.byteBuffer.clear();
               WebRtcAudioTrack.this.byteBuffer.put(WebRtcAudioTrack.this.emptyBytes);
               WebRtcAudioTrack.this.byteBuffer.position(0);
            }

            int bytesWritten = this.writeBytes(WebRtcAudioTrack.this.audioTrack, WebRtcAudioTrack.this.byteBuffer, sizeInBytes);
            if (bytesWritten != sizeInBytes) {
               Logging.e("WebRtcAudioTrackExternal", "AudioTrack.write played invalid number of bytes: " + bytesWritten);
               if (bytesWritten < 0) {
                  this.keepAlive = false;
                  WebRtcAudioTrack.this.reportWebRtcAudioTrackError("AudioTrack.write failed: " + bytesWritten);
               }
            }
         }

         if (WebRtcAudioTrack.this.audioTrack != null) {
            Logging.d("WebRtcAudioTrackExternal", "Calling AudioTrack.stop...");

            try {
               WebRtcAudioTrack.this.audioTrack.stop();
               Logging.d("WebRtcAudioTrackExternal", "AudioTrack.stop is done.");
               WebRtcAudioTrack.this.doAudioTrackStateCallback(1);
            } catch (IllegalStateException var3) {
               Logging.e("WebRtcAudioTrackExternal", "AudioTrack.stop failed: " + var3.getMessage());
            }
         }

      }

      private int writeBytes(AudioTrack audioTrack, ByteBuffer byteBuffer, int sizeInBytes) {
         return VERSION.SDK_INT >= 21 ? audioTrack.write(byteBuffer, sizeInBytes, 0) : audioTrack.write(byteBuffer.array(), byteBuffer.arrayOffset(), sizeInBytes);
      }

      public void stopThread() {
         Logging.d("WebRtcAudioTrackExternal", "stopThread");
         this.keepAlive = false;
      }
   }
}
