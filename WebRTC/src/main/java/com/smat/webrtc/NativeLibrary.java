package com.smat.webrtc;
/* loaded from: input.aar:classes.jar:org/webrtc/NativeLibrary.class */
class NativeLibrary {
    private static String TAG = "NativeLibrary";
    private static Object lock = new Object();
    private static boolean libraryLoaded;

    NativeLibrary() {
    }

    /* loaded from: input.aar:classes.jar:org/webrtc/NativeLibrary$DefaultLoader.class */
    static class DefaultLoader implements NativeLibraryLoader {
        @Override // org.webrtc.NativeLibraryLoader
        public boolean load(String name) {
            Logging.d(NativeLibrary.TAG, "Loading library: " + name);
            try {
                System.loadLibrary(name);
                return true;
            } catch (UnsatisfiedLinkError e) {
                Logging.e(NativeLibrary.TAG, "Failed to load native library: " + name, e);
                return false;
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public static void initialize(NativeLibraryLoader loader, String libraryName) {
        synchronized (lock) {
            if (libraryLoaded) {
                Logging.d(TAG, "Native library has already been loaded.");
                return;
            }
            Logging.d(TAG, "Loading native library: " + libraryName);
            libraryLoaded = loader.load(libraryName);
        }
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public static boolean isLoaded() {
        boolean z;
        synchronized (lock) {
            z = libraryLoaded;
        }
        return z;
    }
}
