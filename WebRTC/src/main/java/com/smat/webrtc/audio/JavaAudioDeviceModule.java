package com.smat.webrtc.audio;

import android.content.Context;
import android.media.AudioManager;
import org.webrtc.JniCommon;
import org.webrtc.Logging;

public class JavaAudioDeviceModule implements AudioDeviceModule {
   private static final String TAG = "JavaAudioDeviceModule";
   private final Context context;
   private final AudioManager audioManager;
   private final WebRtcAudioRecord audioInput;
   private final WebRtcAudioTrack audioOutput;
   private final int inputSampleRate;
   private final int outputSampleRate;
   private final boolean useStereoInput;
   private final boolean useStereoOutput;
   private final Object nativeLock;
   private long nativeAudioDeviceModule;

   public static Builder builder(Context context) {
      return new Builder(context);
   }

   public static boolean isBuiltInAcousticEchoCancelerSupported() {
      return WebRtcAudioEffects.isAcousticEchoCancelerSupported();
   }

   public static boolean isBuiltInNoiseSuppressorSupported() {
      return WebRtcAudioEffects.isNoiseSuppressorSupported();
   }

   private JavaAudioDeviceModule(Context context, AudioManager audioManager, WebRtcAudioRecord audioInput, WebRtcAudioTrack audioOutput, int inputSampleRate, int outputSampleRate, boolean useStereoInput, boolean useStereoOutput) {
      this.nativeLock = new Object();
      this.context = context;
      this.audioManager = audioManager;
      this.audioInput = audioInput;
      this.audioOutput = audioOutput;
      this.inputSampleRate = inputSampleRate;
      this.outputSampleRate = outputSampleRate;
      this.useStereoInput = useStereoInput;
      this.useStereoOutput = useStereoOutput;
   }

   public long getNativeAudioDeviceModulePointer() {
      synchronized(this.nativeLock) {
         if (this.nativeAudioDeviceModule == 0L) {
            this.nativeAudioDeviceModule = nativeCreateAudioDeviceModule(this.context, this.audioManager, this.audioInput, this.audioOutput, this.inputSampleRate, this.outputSampleRate, this.useStereoInput, this.useStereoOutput);
         }

         return this.nativeAudioDeviceModule;
      }
   }

   public void release() {
      synchronized(this.nativeLock) {
         if (this.nativeAudioDeviceModule != 0L) {
            JniCommon.nativeReleaseRef(this.nativeAudioDeviceModule);
            this.nativeAudioDeviceModule = 0L;
         }

      }
   }

   public void setSpeakerMute(boolean mute) {
      Logging.d("JavaAudioDeviceModule", "setSpeakerMute: " + mute);
      this.audioOutput.setSpeakerMute(mute);
   }

   public void setMicrophoneMute(boolean mute) {
      Logging.d("JavaAudioDeviceModule", "setMicrophoneMute: " + mute);
      this.audioInput.setMicrophoneMute(mute);
   }

   private static native long nativeCreateAudioDeviceModule(Context var0, AudioManager var1, WebRtcAudioRecord var2, WebRtcAudioTrack var3, int var4, int var5, boolean var6, boolean var7);

   // $FF: synthetic method
   JavaAudioDeviceModule(Context x0, AudioManager x1, WebRtcAudioRecord x2, WebRtcAudioTrack x3, int x4, int x5, boolean x6, boolean x7, Object x8) {
      this(x0, x1, x2, x3, x4, x5, x6, x7);
   }

   public interface AudioTrackStateCallback {
      void onWebRtcAudioTrackStart();

      void onWebRtcAudioTrackStop();
   }

   public interface AudioTrackErrorCallback {
      void onWebRtcAudioTrackInitError(String var1);

      void onWebRtcAudioTrackStartError(AudioTrackStartErrorCode var1, String var2);

      void onWebRtcAudioTrackError(String var1);
   }

   public static enum AudioTrackStartErrorCode {
      AUDIO_TRACK_START_EXCEPTION,
      AUDIO_TRACK_START_STATE_MISMATCH;
   }

   public interface SamplesReadyCallback {
      void onWebRtcAudioRecordSamplesReady(AudioSamples var1);
   }

   public static class AudioSamples {
      private final int audioFormat;
      private final int channelCount;
      private final int sampleRate;
      private final byte[] data;

      public AudioSamples(int audioFormat, int channelCount, int sampleRate, byte[] data) {
         this.audioFormat = audioFormat;
         this.channelCount = channelCount;
         this.sampleRate = sampleRate;
         this.data = data;
      }

      public int getAudioFormat() {
         return this.audioFormat;
      }

      public int getChannelCount() {
         return this.channelCount;
      }

      public int getSampleRate() {
         return this.sampleRate;
      }

      public byte[] getData() {
         return this.data;
      }
   }

   public interface AudioRecordStateCallback {
      void onWebRtcAudioRecordStart();

      void onWebRtcAudioRecordStop();
   }

   public interface AudioRecordErrorCallback {
      void onWebRtcAudioRecordInitError(String var1);

      void onWebRtcAudioRecordStartError(AudioRecordStartErrorCode var1, String var2);

      void onWebRtcAudioRecordError(String var1);
   }

   public static enum AudioRecordStartErrorCode {
      AUDIO_RECORD_START_EXCEPTION,
      AUDIO_RECORD_START_STATE_MISMATCH;
   }

   public static class Builder {
      private final Context context;
      private final AudioManager audioManager;
      private int inputSampleRate;
      private int outputSampleRate;
      private int audioSource;
      private int audioFormat;
      private AudioTrackErrorCallback audioTrackErrorCallback;
      private AudioRecordErrorCallback audioRecordErrorCallback;
      private SamplesReadyCallback samplesReadyCallback;
      private AudioTrackStateCallback audioTrackStateCallback;
      private AudioRecordStateCallback audioRecordStateCallback;
      private boolean useHardwareAcousticEchoCanceler;
      private boolean useHardwareNoiseSuppressor;
      private boolean useStereoInput;
      private boolean useStereoOutput;

      private Builder(Context context) {
         this.audioSource = 7;
         this.audioFormat = 2;
         this.useHardwareAcousticEchoCanceler = JavaAudioDeviceModule.isBuiltInAcousticEchoCancelerSupported();
         this.useHardwareNoiseSuppressor = JavaAudioDeviceModule.isBuiltInNoiseSuppressorSupported();
         this.context = context;
         this.audioManager = (AudioManager)context.getSystemService("audio");
         this.inputSampleRate = WebRtcAudioManager.getSampleRate(this.audioManager);
         this.outputSampleRate = WebRtcAudioManager.getSampleRate(this.audioManager);
      }

      public Builder setSampleRate(int sampleRate) {
         Logging.d("JavaAudioDeviceModule", "Input/Output sample rate overridden to: " + sampleRate);
         this.inputSampleRate = sampleRate;
         this.outputSampleRate = sampleRate;
         return this;
      }

      public Builder setInputSampleRate(int inputSampleRate) {
         Logging.d("JavaAudioDeviceModule", "Input sample rate overridden to: " + inputSampleRate);
         this.inputSampleRate = inputSampleRate;
         return this;
      }

      public Builder setOutputSampleRate(int outputSampleRate) {
         Logging.d("JavaAudioDeviceModule", "Output sample rate overridden to: " + outputSampleRate);
         this.outputSampleRate = outputSampleRate;
         return this;
      }

      public Builder setAudioSource(int audioSource) {
         this.audioSource = audioSource;
         return this;
      }

      public Builder setAudioFormat(int audioFormat) {
         this.audioFormat = audioFormat;
         return this;
      }

      public Builder setAudioTrackErrorCallback(AudioTrackErrorCallback audioTrackErrorCallback) {
         this.audioTrackErrorCallback = audioTrackErrorCallback;
         return this;
      }

      public Builder setAudioRecordErrorCallback(AudioRecordErrorCallback audioRecordErrorCallback) {
         this.audioRecordErrorCallback = audioRecordErrorCallback;
         return this;
      }

      public Builder setSamplesReadyCallback(SamplesReadyCallback samplesReadyCallback) {
         this.samplesReadyCallback = samplesReadyCallback;
         return this;
      }

      public Builder setAudioTrackStateCallback(AudioTrackStateCallback audioTrackStateCallback) {
         this.audioTrackStateCallback = audioTrackStateCallback;
         return this;
      }

      public Builder setAudioRecordStateCallback(AudioRecordStateCallback audioRecordStateCallback) {
         this.audioRecordStateCallback = audioRecordStateCallback;
         return this;
      }

      public Builder setUseHardwareNoiseSuppressor(boolean useHardwareNoiseSuppressor) {
         if (useHardwareNoiseSuppressor && !JavaAudioDeviceModule.isBuiltInNoiseSuppressorSupported()) {
            Logging.e("JavaAudioDeviceModule", "HW NS not supported");
            useHardwareNoiseSuppressor = false;
         }

         this.useHardwareNoiseSuppressor = useHardwareNoiseSuppressor;
         return this;
      }

      public Builder setUseHardwareAcousticEchoCanceler(boolean useHardwareAcousticEchoCanceler) {
         if (useHardwareAcousticEchoCanceler && !JavaAudioDeviceModule.isBuiltInAcousticEchoCancelerSupported()) {
            Logging.e("JavaAudioDeviceModule", "HW AEC not supported");
            useHardwareAcousticEchoCanceler = false;
         }

         this.useHardwareAcousticEchoCanceler = useHardwareAcousticEchoCanceler;
         return this;
      }

      public Builder setUseStereoInput(boolean useStereoInput) {
         this.useStereoInput = useStereoInput;
         return this;
      }

      public Builder setUseStereoOutput(boolean useStereoOutput) {
         this.useStereoOutput = useStereoOutput;
         return this;
      }

      public AudioDeviceModule createAudioDeviceModule() {
         Logging.d("JavaAudioDeviceModule", "createAudioDeviceModule");
         if (this.useHardwareNoiseSuppressor) {
            Logging.d("JavaAudioDeviceModule", "HW NS will be used.");
         } else {
            if (JavaAudioDeviceModule.isBuiltInNoiseSuppressorSupported()) {
               Logging.d("JavaAudioDeviceModule", "Overriding default behavior; now using WebRTC NS!");
            }

            Logging.d("JavaAudioDeviceModule", "HW NS will not be used.");
         }

         if (this.useHardwareAcousticEchoCanceler) {
            Logging.d("JavaAudioDeviceModule", "HW AEC will be used.");
         } else {
            if (JavaAudioDeviceModule.isBuiltInAcousticEchoCancelerSupported()) {
               Logging.d("JavaAudioDeviceModule", "Overriding default behavior; now using WebRTC AEC!");
            }

            Logging.d("JavaAudioDeviceModule", "HW AEC will not be used.");
         }

         WebRtcAudioRecord audioInput = new WebRtcAudioRecord(this.context, this.audioManager, this.audioSource, this.audioFormat, this.audioRecordErrorCallback, this.audioRecordStateCallback, this.samplesReadyCallback, this.useHardwareAcousticEchoCanceler, this.useHardwareNoiseSuppressor);
         WebRtcAudioTrack audioOutput = new WebRtcAudioTrack(this.context, this.audioManager, this.audioTrackErrorCallback, this.audioTrackStateCallback);
         return new JavaAudioDeviceModule(this.context, this.audioManager, audioInput, audioOutput, this.inputSampleRate, this.outputSampleRate, this.useStereoInput, this.useStereoOutput);
      }

      // $FF: synthetic method
      Builder(Context x0, Object x1) {
         this(x0);
      }
   }
}
