package com.smat.webrtc;

import android.content.Context;
import android.os.Build;
import android.support.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import org.webrtc.NetworkMonitorAutoDetect;
/* loaded from: input.aar:classes.jar:org/webrtc/NetworkMonitor.class */
public class NetworkMonitor {
    private static final String TAG = "NetworkMonitor";
    private final ArrayList<Long> nativeNetworkObservers;
    private final ArrayList<NetworkObserver> networkObservers;
    private final Object autoDetectLock;
    @Nullable
    private NetworkMonitorAutoDetect autoDetect;
    private int numObservers;
    private volatile NetworkMonitorAutoDetect.ConnectionType currentConnectionType;

    /* loaded from: input.aar:classes.jar:org/webrtc/NetworkMonitor$NetworkObserver.class */
    public interface NetworkObserver {
        void onConnectionTypeChanged(NetworkMonitorAutoDetect.ConnectionType connectionType);
    }

    private native void nativeNotifyConnectionTypeChanged(long j);

    private native void nativeNotifyOfNetworkConnect(long j, NetworkMonitorAutoDetect.NetworkInformation networkInformation);

    private native void nativeNotifyOfNetworkDisconnect(long j, long j2);

    private native void nativeNotifyOfActiveNetworkList(long j, NetworkMonitorAutoDetect.NetworkInformation[] networkInformationArr);

    /* JADX INFO: Access modifiers changed from: private */
    /* loaded from: input.aar:classes.jar:org/webrtc/NetworkMonitor$InstanceHolder.class */
    public static class InstanceHolder {
        static final NetworkMonitor instance = new NetworkMonitor();

        private InstanceHolder() {
        }
    }

    private NetworkMonitor() {
        this.autoDetectLock = new Object();
        this.nativeNetworkObservers = new ArrayList<>();
        this.networkObservers = new ArrayList<>();
        this.numObservers = 0;
        this.currentConnectionType = NetworkMonitorAutoDetect.ConnectionType.CONNECTION_UNKNOWN;
    }

    @Deprecated
    public static void init(Context context) {
    }

    @CalledByNative
    public static NetworkMonitor getInstance() {
        return InstanceHolder.instance;
    }

    private static void assertIsTrue(boolean condition) {
        if (!condition) {
            throw new AssertionError("Expected to be true");
        }
    }

    public void startMonitoring(Context applicationContext) {
        synchronized (this.autoDetectLock) {
            this.numObservers++;
            if (this.autoDetect == null) {
                this.autoDetect = createAutoDetect(applicationContext);
            }
            this.currentConnectionType = NetworkMonitorAutoDetect.getConnectionType(this.autoDetect.getCurrentNetworkState());
        }
    }

    @Deprecated
    public void startMonitoring() {
        startMonitoring(ContextUtils.getApplicationContext());
    }

    @CalledByNative
    private void startMonitoring(@Nullable Context applicationContext, long nativeObserver) {
        Logging.d(TAG, "Start monitoring with native observer " + nativeObserver);
        startMonitoring(applicationContext != null ? applicationContext : ContextUtils.getApplicationContext());
        synchronized (this.nativeNetworkObservers) {
            this.nativeNetworkObservers.add(Long.valueOf(nativeObserver));
        }
        updateObserverActiveNetworkList(nativeObserver);
        notifyObserversOfConnectionTypeChange(this.currentConnectionType);
    }

    public void stopMonitoring() {
        synchronized (this.autoDetectLock) {
            int i = this.numObservers - 1;
            this.numObservers = i;
            if (i == 0) {
                this.autoDetect.destroy();
                this.autoDetect = null;
            }
        }
    }

    @CalledByNative
    private void stopMonitoring(long nativeObserver) {
        Logging.d(TAG, "Stop monitoring with native observer " + nativeObserver);
        stopMonitoring();
        synchronized (this.nativeNetworkObservers) {
            this.nativeNetworkObservers.remove(Long.valueOf(nativeObserver));
        }
    }

    @CalledByNative
    private boolean networkBindingSupported() {
        boolean z;
        synchronized (this.autoDetectLock) {
            z = this.autoDetect != null && this.autoDetect.supportNetworkCallback();
        }
        return z;
    }

    @CalledByNative
    private static int androidSdkInt() {
        return Build.VERSION.SDK_INT;
    }

    private NetworkMonitorAutoDetect.ConnectionType getCurrentConnectionType() {
        return this.currentConnectionType;
    }

    private long getCurrentDefaultNetId() {
        long defaultNetId;
        synchronized (this.autoDetectLock) {
            defaultNetId = this.autoDetect == null ? -1L : this.autoDetect.getDefaultNetId();
        }
        return defaultNetId;
    }

    private NetworkMonitorAutoDetect createAutoDetect(Context appContext) {
        return new NetworkMonitorAutoDetect(new NetworkMonitorAutoDetect.Observer() { // from class: org.webrtc.NetworkMonitor.1
            @Override // org.webrtc.NetworkMonitorAutoDetect.Observer
            public void onConnectionTypeChanged(NetworkMonitorAutoDetect.ConnectionType newConnectionType) {
                NetworkMonitor.this.updateCurrentConnectionType(newConnectionType);
            }

            @Override // org.webrtc.NetworkMonitorAutoDetect.Observer
            public void onNetworkConnect(NetworkMonitorAutoDetect.NetworkInformation networkInfo) {
                NetworkMonitor.this.notifyObserversOfNetworkConnect(networkInfo);
            }

            @Override // org.webrtc.NetworkMonitorAutoDetect.Observer
            public void onNetworkDisconnect(long networkHandle) {
                NetworkMonitor.this.notifyObserversOfNetworkDisconnect(networkHandle);
            }
        }, appContext);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void updateCurrentConnectionType(NetworkMonitorAutoDetect.ConnectionType newConnectionType) {
        this.currentConnectionType = newConnectionType;
        notifyObserversOfConnectionTypeChange(newConnectionType);
    }

    private void notifyObserversOfConnectionTypeChange(NetworkMonitorAutoDetect.ConnectionType newConnectionType) {
        List<NetworkObserver> javaObservers;
        List<Long> nativeObservers = getNativeNetworkObserversSync();
        for (Long nativeObserver : nativeObservers) {
            nativeNotifyConnectionTypeChanged(nativeObserver.longValue());
        }
        synchronized (this.networkObservers) {
            javaObservers = new ArrayList<>(this.networkObservers);
        }
        for (NetworkObserver observer : javaObservers) {
            observer.onConnectionTypeChanged(newConnectionType);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void notifyObserversOfNetworkConnect(NetworkMonitorAutoDetect.NetworkInformation networkInfo) {
        List<Long> nativeObservers = getNativeNetworkObserversSync();
        for (Long nativeObserver : nativeObservers) {
            nativeNotifyOfNetworkConnect(nativeObserver.longValue(), networkInfo);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void notifyObserversOfNetworkDisconnect(long networkHandle) {
        List<Long> nativeObservers = getNativeNetworkObserversSync();
        for (Long nativeObserver : nativeObservers) {
            nativeNotifyOfNetworkDisconnect(nativeObserver.longValue(), networkHandle);
        }
    }

    private void updateObserverActiveNetworkList(long nativeObserver) {
        List<NetworkMonitorAutoDetect.NetworkInformation> networkInfoList;
        synchronized (this.autoDetectLock) {
            networkInfoList = this.autoDetect == null ? null : this.autoDetect.getActiveNetworkList();
        }
        if (networkInfoList == null || networkInfoList.size() == 0) {
            return;
        }
        NetworkMonitorAutoDetect.NetworkInformation[] networkInfos = new NetworkMonitorAutoDetect.NetworkInformation[networkInfoList.size()];
        nativeNotifyOfActiveNetworkList(nativeObserver, (NetworkMonitorAutoDetect.NetworkInformation[]) networkInfoList.toArray(networkInfos));
    }

    private List<Long> getNativeNetworkObserversSync() {
        ArrayList arrayList;
        synchronized (this.nativeNetworkObservers) {
            arrayList = new ArrayList(this.nativeNetworkObservers);
        }
        return arrayList;
    }

    @Deprecated
    public static void addNetworkObserver(NetworkObserver observer) {
        getInstance().addObserver(observer);
    }

    public void addObserver(NetworkObserver observer) {
        synchronized (this.networkObservers) {
            this.networkObservers.add(observer);
        }
    }

    @Deprecated
    public static void removeNetworkObserver(NetworkObserver observer) {
        getInstance().removeObserver(observer);
    }

    public void removeObserver(NetworkObserver observer) {
        synchronized (this.networkObservers) {
            this.networkObservers.remove(observer);
        }
    }

    public static boolean isOnline() {
        NetworkMonitorAutoDetect.ConnectionType connectionType = getInstance().getCurrentConnectionType();
        return connectionType != NetworkMonitorAutoDetect.ConnectionType.CONNECTION_NONE;
    }

    @Nullable
    NetworkMonitorAutoDetect getNetworkMonitorAutoDetect() {
        NetworkMonitorAutoDetect networkMonitorAutoDetect;
        synchronized (this.autoDetectLock) {
            networkMonitorAutoDetect = this.autoDetect;
        }
        return networkMonitorAutoDetect;
    }

    int getNumObservers() {
        int i;
        synchronized (this.autoDetectLock) {
            i = this.numObservers;
        }
        return i;
    }

    static NetworkMonitorAutoDetect createAndSetAutoDetectForTest(Context context) {
        NetworkMonitor networkMonitor = getInstance();
        NetworkMonitorAutoDetect autoDetect = networkMonitor.createAutoDetect(context);
        networkMonitor.autoDetect = autoDetect;
        return autoDetect;
    }
}
