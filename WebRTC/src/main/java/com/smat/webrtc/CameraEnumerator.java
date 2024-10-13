package com.smat.webrtc;

import java.util.List;
import org.webrtc.CameraEnumerationAndroid;
import org.webrtc.CameraVideoCapturer;
/* loaded from: input.aar:classes.jar:org/webrtc/CameraEnumerator.class */
public interface CameraEnumerator {
    String[] getDeviceNames();

    boolean isFrontFacing(String str);

    boolean isBackFacing(String str);

    List<CameraEnumerationAndroid.CaptureFormat> getSupportedFormats(String str);

    CameraVideoCapturer createCapturer(String str, CameraVideoCapturer.CameraEventsHandler cameraEventsHandler);
}
