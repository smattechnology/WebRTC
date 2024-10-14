package com.smat.webrtc;

import android.content.Context;
import android.hardware.Camera;
import android.hardware.Camera.CameraInfo;
import android.hardware.Camera.ErrorCallback;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.PreviewCallback;
import android.os.Handler;
import android.os.SystemClock;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

class Camera1Session implements CameraSession {
   private static final String TAG = "Camera1Session";
   private static final int NUMBER_OF_CAPTURE_BUFFERS = 3;
   private static final Histogram camera1StartTimeMsHistogram = Histogram.createCounts("WebRTC.Android.Camera1.StartTimeMs", 1, 10000, 50);
   private static final Histogram camera1StopTimeMsHistogram = Histogram.createCounts("WebRTC.Android.Camera1.StopTimeMs", 1, 10000, 50);
   private static final Histogram camera1ResolutionHistogram;
   private final Handler cameraThreadHandler;
   private final CameraSession.Events events;
   private final boolean captureToTexture;
   private final Context applicationContext;
   private final SurfaceTextureHelper surfaceTextureHelper;
   private final int cameraId;
   private final Camera camera;
   private final CameraInfo info;
   private final CameraEnumerationAndroid.CaptureFormat captureFormat;
   private final long constructionTimeNs;
   private SessionState state;
   private boolean firstFrameReported;

   public static void create(CameraSession.CreateSessionCallback callback, CameraSession.Events events, boolean captureToTexture, Context applicationContext, SurfaceTextureHelper surfaceTextureHelper, int cameraId, int width, int height, int framerate) {
      long constructionTimeNs = System.nanoTime();
      Logging.d("Camera1Session", "Open camera " + cameraId);
      events.onCameraOpening();

      Camera camera;
      try {
         camera = Camera.open(cameraId);
      } catch (RuntimeException var19) {
         callback.onFailure(CameraSession.FailureType.ERROR, var19.getMessage());
         return;
      }

      if (camera == null) {
         callback.onFailure(CameraSession.FailureType.ERROR, "android.hardware.Camera.open returned null for camera id = " + cameraId);
      } else {
         try {
            camera.setPreviewTexture(surfaceTextureHelper.getSurfaceTexture());
         } catch (RuntimeException | IOException var18) {
            camera.release();
            callback.onFailure(CameraSession.FailureType.ERROR, var18.getMessage());
            return;
         }

         CameraInfo info = new CameraInfo();
         Camera.getCameraInfo(cameraId, info);

         CameraEnumerationAndroid.CaptureFormat captureFormat;
         try {
            Parameters parameters = camera.getParameters();
            captureFormat = findClosestCaptureFormat(parameters, width, height, framerate);
            Size pictureSize = findClosestPictureSize(parameters, width, height);
            updateCameraParameters(camera, parameters, captureFormat, pictureSize, captureToTexture);
         } catch (RuntimeException var17) {
            camera.release();
            callback.onFailure(CameraSession.FailureType.ERROR, var17.getMessage());
            return;
         }

         if (!captureToTexture) {
            int frameSize = captureFormat.frameSize();

            for(int i = 0; i < 3; ++i) {
               ByteBuffer buffer = ByteBuffer.allocateDirect(frameSize);
               camera.addCallbackBuffer(buffer.array());
            }
         }

         camera.setDisplayOrientation(0);
         callback.onDone(new Camera1Session(events, captureToTexture, applicationContext, surfaceTextureHelper, cameraId, camera, info, captureFormat, constructionTimeNs));
      }
   }

   private static void updateCameraParameters(Camera camera, Parameters parameters, CameraEnumerationAndroid.CaptureFormat captureFormat, Size pictureSize, boolean captureToTexture) {
      List<String> focusModes = parameters.getSupportedFocusModes();
      parameters.setPreviewFpsRange(captureFormat.framerate.min, captureFormat.framerate.max);
      parameters.setPreviewSize(captureFormat.width, captureFormat.height);
      parameters.setPictureSize(pictureSize.width, pictureSize.height);
      if (!captureToTexture) {
         Objects.requireNonNull(captureFormat);
         parameters.setPreviewFormat(17);
      }

      if (parameters.isVideoStabilizationSupported()) {
         parameters.setVideoStabilization(true);
      }

      if (focusModes.contains("continuous-video")) {
         parameters.setFocusMode("continuous-video");
      }

      camera.setParameters(parameters);
   }

   private static CameraEnumerationAndroid.CaptureFormat findClosestCaptureFormat(Parameters parameters, int width, int height, int framerate) {
      List<CameraEnumerationAndroid.CaptureFormat.FramerateRange> supportedFramerates = Camera1Enumerator.convertFramerates(parameters.getSupportedPreviewFpsRange());
      Logging.d("Camera1Session", "Available fps ranges: " + supportedFramerates);
      CameraEnumerationAndroid.CaptureFormat.FramerateRange fpsRange = CameraEnumerationAndroid.getClosestSupportedFramerateRange(supportedFramerates, framerate);
      Size previewSize = CameraEnumerationAndroid.getClosestSupportedSize(Camera1Enumerator.convertSizes(parameters.getSupportedPreviewSizes()), width, height);
      CameraEnumerationAndroid.reportCameraResolution(camera1ResolutionHistogram, previewSize);
      return new CameraEnumerationAndroid.CaptureFormat(previewSize.width, previewSize.height, fpsRange);
   }

   private static Size findClosestPictureSize(Parameters parameters, int width, int height) {
      return CameraEnumerationAndroid.getClosestSupportedSize(Camera1Enumerator.convertSizes(parameters.getSupportedPictureSizes()), width, height);
   }

   private Camera1Session(CameraSession.Events events, boolean captureToTexture, Context applicationContext, SurfaceTextureHelper surfaceTextureHelper, int cameraId, Camera camera, CameraInfo info, CameraEnumerationAndroid.CaptureFormat captureFormat, long constructionTimeNs) {
      Logging.d("Camera1Session", "Create new camera1 session on camera " + cameraId);
      this.cameraThreadHandler = new Handler();
      this.events = events;
      this.captureToTexture = captureToTexture;
      this.applicationContext = applicationContext;
      this.surfaceTextureHelper = surfaceTextureHelper;
      this.cameraId = cameraId;
      this.camera = camera;
      this.info = info;
      this.captureFormat = captureFormat;
      this.constructionTimeNs = constructionTimeNs;
      surfaceTextureHelper.setTextureSize(captureFormat.width, captureFormat.height);
      this.startCapturing();
   }

   public void stop() {
      Logging.d("Camera1Session", "Stop camera1 session on camera " + this.cameraId);
      this.checkIsOnCameraThread();
      if (this.state != SessionState.STOPPED) {
         long stopStartTime = System.nanoTime();
         this.stopInternal();
         int stopTimeMs = (int)TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - stopStartTime);
         camera1StopTimeMsHistogram.addSample(stopTimeMs);
      }

   }

   private void startCapturing() {
      Logging.d("Camera1Session", "Start capturing");
      this.checkIsOnCameraThread();
      this.state = SessionState.RUNNING;
      this.camera.setErrorCallback(new ErrorCallback() {
         public void onError(int error, Camera camera) {
            String errorMessage;
            if (error == 100) {
               errorMessage = "Camera server died!";
            } else {
               errorMessage = "Camera error: " + error;
            }

            Logging.e("Camera1Session", errorMessage);
            Camera1Session.this.stopInternal();
            if (error == 2) {
               Camera1Session.this.events.onCameraDisconnected(Camera1Session.this);
            } else {
               Camera1Session.this.events.onCameraError(Camera1Session.this, errorMessage);
            }

         }
      });
      if (this.captureToTexture) {
         this.listenForTextureFrames();
      } else {
         this.listenForBytebufferFrames();
      }

      try {
         this.camera.startPreview();
      } catch (RuntimeException var2) {
         this.stopInternal();
         this.events.onCameraError(this, var2.getMessage());
      }

   }

   private void stopInternal() {
      Logging.d("Camera1Session", "Stop internal");
      this.checkIsOnCameraThread();
      if (this.state == SessionState.STOPPED) {
         Logging.d("Camera1Session", "Camera is already stopped");
      } else {
         this.state = SessionState.STOPPED;
         this.surfaceTextureHelper.stopListening();
         this.camera.stopPreview();
         this.camera.release();
         this.events.onCameraClosed(this);
         Logging.d("Camera1Session", "Stop done");
      }
   }

   private void listenForTextureFrames() {
      this.surfaceTextureHelper.startListening((frame) -> {
         this.checkIsOnCameraThread();
         if (this.state != SessionState.RUNNING) {
            Logging.d("Camera1Session", "Texture frame captured but camera is no longer running.");
         } else {
            if (!this.firstFrameReported) {
               int startTimeMs = (int)TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - this.constructionTimeNs);
               camera1StartTimeMsHistogram.addSample(startTimeMs);
               this.firstFrameReported = true;
            }

            VideoFrame modifiedFrame = new VideoFrame(CameraSession.createTextureBufferWithModifiedTransformMatrix((TextureBufferImpl)frame.getBuffer(), this.info.facing == 1, 0), this.getFrameOrientation(), frame.getTimestampNs());
            this.events.onFrameCaptured(this, modifiedFrame);
            modifiedFrame.release();
         }
      });
   }

   private void listenForBytebufferFrames() {
      this.camera.setPreviewCallbackWithBuffer(new PreviewCallback() {
         public void onPreviewFrame(byte[] data, Camera callbackCamera) {
            Camera1Session.this.checkIsOnCameraThread();
            if (callbackCamera != Camera1Session.this.camera) {
               Logging.e("Camera1Session", "Callback from a different camera. This should never happen.");
            } else if (Camera1Session.this.state != SessionState.RUNNING) {
               Logging.d("Camera1Session", "Bytebuffer frame captured but camera is no longer running.");
            } else {
               long captureTimeNs = TimeUnit.MILLISECONDS.toNanos(SystemClock.elapsedRealtime());
               if (!Camera1Session.this.firstFrameReported) {
                  int startTimeMs = (int)TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - Camera1Session.this.constructionTimeNs);
                  Camera1Session.camera1StartTimeMsHistogram.addSample(startTimeMs);
                  Camera1Session.this.firstFrameReported = true;
               }

               VideoFrame.Buffer frameBuffer = new NV21Buffer(data, Camera1Session.this.captureFormat.width, Camera1Session.this.captureFormat.height, () -> {
                  Camera1Session.this.cameraThreadHandler.post(() -> {
                     if (Camera1Session.this.state == SessionState.RUNNING) {
                        Camera1Session.this.camera.addCallbackBuffer(data);
                     }

                  });
               });
               VideoFrame frame = new VideoFrame(frameBuffer, Camera1Session.this.getFrameOrientation(), captureTimeNs);
               Camera1Session.this.events.onFrameCaptured(Camera1Session.this, frame);
               frame.release();
            }
         }
      });
   }

   private int getFrameOrientation() {
      int rotation = CameraSession.getDeviceOrientation(this.applicationContext);
      if (this.info.facing == 0) {
         rotation = 360 - rotation;
      }

      return (this.info.orientation + rotation) % 360;
   }

   private void checkIsOnCameraThread() {
      if (Thread.currentThread() != this.cameraThreadHandler.getLooper().getThread()) {
         throw new IllegalStateException("Wrong thread");
      }
   }

   static {
      camera1ResolutionHistogram = Histogram.createEnumeration("WebRTC.Android.Camera1.Resolution", CameraEnumerationAndroid.COMMON_RESOLUTIONS.size());
   }

   private static enum SessionState {
      RUNNING,
      STOPPED;
   }
}
