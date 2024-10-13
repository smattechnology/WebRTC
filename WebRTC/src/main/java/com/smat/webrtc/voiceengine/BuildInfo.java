package com.smat.webrtc.voiceengine;

import android.os.Build;
/* loaded from: input.aar:classes.jar:org/webrtc/voiceengine/BuildInfo.class */
public final class BuildInfo {
    public static String getDevice() {
        return Build.DEVICE;
    }

    public static String getDeviceModel() {
        return Build.MODEL;
    }

    public static String getProduct() {
        return Build.PRODUCT;
    }

    public static String getBrand() {
        return Build.BRAND;
    }

    public static String getDeviceManufacturer() {
        return Build.MANUFACTURER;
    }

    public static String getAndroidBuildId() {
        return Build.ID;
    }

    public static String getBuildType() {
        return Build.TYPE;
    }

    public static String getBuildRelease() {
        return Build.VERSION.RELEASE;
    }

    public static int getSdkVersion() {
        return Build.VERSION.SDK_INT;
    }
}
