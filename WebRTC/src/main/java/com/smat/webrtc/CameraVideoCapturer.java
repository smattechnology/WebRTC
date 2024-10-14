package com.smat.webrtc;

import android.media.MediaRecorder;

public interface CameraVideoCapturer extends VideoCapturer {
   void switchCamera(CameraSwitchHandler var1);

   /** @deprecated */
   @Deprecated
   default void addMediaRecorderToCamera(MediaRecorder mediaRecorder, MediaRecorderHandler resultHandler) {
      throw new UnsupportedOperationException("Deprecated and not implemented.");
   }

   /** @deprecated */
   @Deprecated
   default void removeMediaRecorderFromCamera(MediaRecorderHandler resultHandler) {
      throw new UnsupportedOperationException("Deprecated and not implemented.");
   }

   public static class CameraStatistics {
      private static final String TAG = "CameraStatistics";
      private static final int CAMERA_OBSERVER_PERIOD_MS = 2000;
      private static final int CAMERA_FREEZE_REPORT_TIMOUT_MS = 4000;
      private final SurfaceTextureHelper surfaceTextureHelper;
      private final CameraEventsHandler eventsHandler;
      private int frameCount;
      private int freezePeriodCount;
      private final Runnable cameraObserver = new Runnable() {
         public void run() {
            int cameraFps = Math.round((float)CameraStatistics.this.frameCount * 1000.0F / 2000.0F);
            Logging.d("CameraStatistics", "Camera fps: " + cameraFps + ".");
            if (CameraStatistics.this.frameCount == 0) {
               ++CameraStatistics.this.freezePeriodCount;
               if (2000 * CameraStatistics.this.freezePeriodCount >= 4000 && CameraStatistics.this.eventsHandler != null) {
                  Logging.e("CameraStatistics", "Camera freezed.");
                  if (CameraStatistics.this.surfaceTextureHelper.isTextureInUse()) {
                     CameraStatistics.this.eventsHandler.onCameraFreezed("Camera failure. Client must return video buffers.");
                  } else {
                     CameraStatistics.this.eventsHandler.onCameraFreezed("Camera failure.");
                  }

                  return;
               }
            } else {
               CameraStatistics.this.freezePeriodCount = 0;
            }

            CameraStatistics.this.frameCount = 0;
            CameraStatistics.this.surfaceTextureHelper.getHandler().postDelayed(this, 2000L);
         }
      };

      public CameraStatistics(SurfaceTextureHelper surfaceTextureHelper, CameraEventsHandler eventsHandler) {
         if (surfaceTextureHelper == null) {
            throw new IllegalArgumentException("SurfaceTextureHelper is null");
         } else {
            this.surfaceTextureHelper = surfaceTextureHelper;
            this.eventsHandler = eventsHandler;
            this.frameCount = 0;
            this.freezePeriodCount = 0;
            surfaceTextureHelper.getHandler().postDelayed(this.cameraObserver, 2000L);
         }
      }

      private void checkThread() {
         if (Thread.currentThread() != this.surfaceTextureHelper.getHandler().getLooper().getThread()) {
            throw new IllegalStateException("Wrong thread");
         }
      }

      public void addFrame() {
         this.checkThread();
         ++this.frameCount;
      }

      public void release() {
         this.surfaceTextureHelper.getHandler().removeCallbacks(this.cameraObserver);
      }
   }

   /** @deprecated */
   @Deprecated
   public interface MediaRecorderHandler {
      void onMediaRecorderSuccess();

      void onMediaRecorderError(String var1);
   }

   public interface CameraSwitchHandler {
      void onCameraSwitchDone(boolean var1);

      void onCameraSwitchError(String var1);
   }

   public interface CameraEventsHandler {
      void onCameraError(String var1);

      void onCameraDisconnected();

      void onCameraFreezed(String var1);

      void onCameraOpening(String var1);

      void onFirstFrameAvailable();

      void onCameraClosed();
   }
}
