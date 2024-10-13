package com.smat.webrtc;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.NetworkRequest;
import android.net.wifi.WifiInfo;
import android.net.wifi.p2p.WifiP2pGroup;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Build;
import android.support.annotation.Nullable;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.webrtc.audio.WebRtcAudioRecord;
/* loaded from: input.aar:classes.jar:org/webrtc/NetworkMonitorAutoDetect.class */
public class NetworkMonitorAutoDetect extends BroadcastReceiver {
    static final long INVALID_NET_ID = -1;
    private static final String TAG = "NetworkMonitorAutoDetect";
    private final Observer observer;
    private final IntentFilter intentFilter;
    private final Context context;
    @Nullable
    private final ConnectivityManager.NetworkCallback mobileNetworkCallback;
    @Nullable
    private final ConnectivityManager.NetworkCallback allNetworkCallback;
    private ConnectivityManagerDelegate connectivityManagerDelegate;
    private WifiManagerDelegate wifiManagerDelegate;
    private WifiDirectManagerDelegate wifiDirectManagerDelegate;
    private boolean isRegistered;
    private ConnectionType connectionType;
    private String wifiSSID;

    /* loaded from: input.aar:classes.jar:org/webrtc/NetworkMonitorAutoDetect$ConnectionType.class */
    public enum ConnectionType {
        CONNECTION_UNKNOWN,
        CONNECTION_ETHERNET,
        CONNECTION_WIFI,
        CONNECTION_4G,
        CONNECTION_3G,
        CONNECTION_2G,
        CONNECTION_UNKNOWN_CELLULAR,
        CONNECTION_BLUETOOTH,
        CONNECTION_VPN,
        CONNECTION_NONE
    }

    /* loaded from: input.aar:classes.jar:org/webrtc/NetworkMonitorAutoDetect$Observer.class */
    public interface Observer {
        void onConnectionTypeChanged(ConnectionType connectionType);

        void onNetworkConnect(NetworkInformation networkInformation);

        void onNetworkDisconnect(long j);
    }

    /* loaded from: input.aar:classes.jar:org/webrtc/NetworkMonitorAutoDetect$IPAddress.class */
    public static class IPAddress {
        public final byte[] address;

        public IPAddress(byte[] address) {
            this.address = address;
        }

        @CalledByNative("IPAddress")
        private byte[] getAddress() {
            return this.address;
        }
    }

    /* loaded from: input.aar:classes.jar:org/webrtc/NetworkMonitorAutoDetect$NetworkInformation.class */
    public static class NetworkInformation {
        public final String name;
        public final ConnectionType type;
        public final ConnectionType underlyingTypeForVpn;
        public final long handle;
        public final IPAddress[] ipAddresses;

        public NetworkInformation(String name, ConnectionType type, ConnectionType underlyingTypeForVpn, long handle, IPAddress[] addresses) {
            this.name = name;
            this.type = type;
            this.underlyingTypeForVpn = underlyingTypeForVpn;
            this.handle = handle;
            this.ipAddresses = addresses;
        }

        @CalledByNative("NetworkInformation")
        private IPAddress[] getIpAddresses() {
            return this.ipAddresses;
        }

        @CalledByNative("NetworkInformation")
        private ConnectionType getConnectionType() {
            return this.type;
        }

        @CalledByNative("NetworkInformation")
        private ConnectionType getUnderlyingConnectionTypeForVpn() {
            return this.underlyingTypeForVpn;
        }

        @CalledByNative("NetworkInformation")
        private long getHandle() {
            return this.handle;
        }

        @CalledByNative("NetworkInformation")
        private String getName() {
            return this.name;
        }
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    /* loaded from: input.aar:classes.jar:org/webrtc/NetworkMonitorAutoDetect$NetworkState.class */
    public static class NetworkState {
        private final boolean connected;
        private final int type;
        private final int subtype;
        private final int underlyingNetworkTypeForVpn;
        private final int underlyingNetworkSubtypeForVpn;

        public NetworkState(boolean connected, int type, int subtype, int underlyingNetworkTypeForVpn, int underlyingNetworkSubtypeForVpn) {
            this.connected = connected;
            this.type = type;
            this.subtype = subtype;
            this.underlyingNetworkTypeForVpn = underlyingNetworkTypeForVpn;
            this.underlyingNetworkSubtypeForVpn = underlyingNetworkSubtypeForVpn;
        }

        public boolean isConnected() {
            return this.connected;
        }

        public int getNetworkType() {
            return this.type;
        }

        public int getNetworkSubType() {
            return this.subtype;
        }

        public int getUnderlyingNetworkTypeForVpn() {
            return this.underlyingNetworkTypeForVpn;
        }

        public int getUnderlyingNetworkSubtypeForVpn() {
            return this.underlyingNetworkSubtypeForVpn;
        }
    }

    @SuppressLint({"NewApi"})
    /* loaded from: input.aar:classes.jar:org/webrtc/NetworkMonitorAutoDetect$SimpleNetworkCallback.class */
    private class SimpleNetworkCallback extends ConnectivityManager.NetworkCallback {
        private SimpleNetworkCallback() {
        }

        @Override // android.net.ConnectivityManager.NetworkCallback
        public void onAvailable(Network network) {
            Logging.d(NetworkMonitorAutoDetect.TAG, "Network becomes available: " + network.toString());
            onNetworkChanged(network);
        }

        @Override // android.net.ConnectivityManager.NetworkCallback
        public void onCapabilitiesChanged(Network network, NetworkCapabilities networkCapabilities) {
            Logging.d(NetworkMonitorAutoDetect.TAG, "capabilities changed: " + networkCapabilities.toString());
            onNetworkChanged(network);
        }

        @Override // android.net.ConnectivityManager.NetworkCallback
        public void onLinkPropertiesChanged(Network network, LinkProperties linkProperties) {
            Logging.d(NetworkMonitorAutoDetect.TAG, "link properties changed: " + linkProperties.toString());
            onNetworkChanged(network);
        }

        @Override // android.net.ConnectivityManager.NetworkCallback
        public void onLosing(Network network, int maxMsToLive) {
            Logging.d(NetworkMonitorAutoDetect.TAG, "Network " + network.toString() + " is about to lose in " + maxMsToLive + "ms");
        }

        @Override // android.net.ConnectivityManager.NetworkCallback
        public void onLost(Network network) {
            Logging.d(NetworkMonitorAutoDetect.TAG, "Network " + network.toString() + " is disconnected");
            NetworkMonitorAutoDetect.this.observer.onNetworkDisconnect(NetworkMonitorAutoDetect.networkToNetId(network));
        }

        private void onNetworkChanged(Network network) {
            NetworkInformation networkInformation = NetworkMonitorAutoDetect.this.connectivityManagerDelegate.networkToInfo(network);
            if (networkInformation != null) {
                NetworkMonitorAutoDetect.this.observer.onNetworkConnect(networkInformation);
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    /* loaded from: input.aar:classes.jar:org/webrtc/NetworkMonitorAutoDetect$ConnectivityManagerDelegate.class */
    public static class ConnectivityManagerDelegate {
        @Nullable
        private final ConnectivityManager connectivityManager;

        ConnectivityManagerDelegate(Context context) {
            this.connectivityManager = (ConnectivityManager) context.getSystemService("connectivity");
        }

        ConnectivityManagerDelegate() {
            this.connectivityManager = null;
        }

        NetworkState getNetworkState() {
            if (this.connectivityManager == null) {
                return new NetworkState(false, -1, -1, -1, -1);
            }
            return getNetworkState(this.connectivityManager.getActiveNetworkInfo());
        }

        @SuppressLint({"NewApi"})
        NetworkState getNetworkState(@Nullable Network network) {
            NetworkInfo underlyingActiveNetworkInfo;
            if (network == null || this.connectivityManager == null) {
                return new NetworkState(false, -1, -1, -1, -1);
            }
            NetworkInfo networkInfo = this.connectivityManager.getNetworkInfo(network);
            if (networkInfo == null) {
                Logging.w(NetworkMonitorAutoDetect.TAG, "Couldn't retrieve information from network " + network.toString());
                return new NetworkState(false, -1, -1, -1, -1);
            } else if (networkInfo.getType() != 17) {
                NetworkCapabilities networkCapabilities = this.connectivityManager.getNetworkCapabilities(network);
                if (networkCapabilities == null || !networkCapabilities.hasTransport(4)) {
                    return getNetworkState(networkInfo);
                }
                return new NetworkState(networkInfo.isConnected(), 17, -1, networkInfo.getType(), networkInfo.getSubtype());
            } else if (networkInfo.getType() == 17) {
                if (Build.VERSION.SDK_INT >= 23 && network.equals(this.connectivityManager.getActiveNetwork()) && (underlyingActiveNetworkInfo = this.connectivityManager.getActiveNetworkInfo()) != null && underlyingActiveNetworkInfo.getType() != 17) {
                    return new NetworkState(networkInfo.isConnected(), 17, -1, underlyingActiveNetworkInfo.getType(), underlyingActiveNetworkInfo.getSubtype());
                }
                return new NetworkState(networkInfo.isConnected(), 17, -1, -1, -1);
            } else {
                return getNetworkState(networkInfo);
            }
        }

        private NetworkState getNetworkState(@Nullable NetworkInfo networkInfo) {
            if (networkInfo == null || !networkInfo.isConnected()) {
                return new NetworkState(false, -1, -1, -1, -1);
            }
            return new NetworkState(true, networkInfo.getType(), networkInfo.getSubtype(), -1, -1);
        }

        @SuppressLint({"NewApi"})
        Network[] getAllNetworks() {
            if (this.connectivityManager == null) {
                return new Network[0];
            }
            return this.connectivityManager.getAllNetworks();
        }

        @Nullable
        List<NetworkInformation> getActiveNetworkList() {
            Network[] allNetworks;
            if (!supportNetworkCallback()) {
                return null;
            }
            ArrayList<NetworkInformation> netInfoList = new ArrayList<>();
            for (Network network : getAllNetworks()) {
                NetworkInformation info = networkToInfo(network);
                if (info != null) {
                    netInfoList.add(info);
                }
            }
            return netInfoList;
        }

        @SuppressLint({"NewApi"})
        long getDefaultNetId() {
            NetworkInfo networkInfo;
            if (!supportNetworkCallback()) {
                return NetworkMonitorAutoDetect.INVALID_NET_ID;
            }
            NetworkInfo defaultNetworkInfo = this.connectivityManager.getActiveNetworkInfo();
            if (defaultNetworkInfo == null) {
                return NetworkMonitorAutoDetect.INVALID_NET_ID;
            }
            Network[] networks = getAllNetworks();
            long defaultNetId = -1;
            for (Network network : networks) {
                if (hasInternetCapability(network) && (networkInfo = this.connectivityManager.getNetworkInfo(network)) != null && networkInfo.getType() == defaultNetworkInfo.getType()) {
                    if (defaultNetId == NetworkMonitorAutoDetect.INVALID_NET_ID) {
                        defaultNetId = NetworkMonitorAutoDetect.networkToNetId(network);
                    } else {
                        throw new RuntimeException("Multiple connected networks of same type are not supported.");
                    }
                }
            }
            return defaultNetId;
        }

        /* JADX INFO: Access modifiers changed from: private */
        @SuppressLint({"NewApi"})
        @Nullable
        public NetworkInformation networkToInfo(@Nullable Network network) {
            if (network == null || this.connectivityManager == null) {
                return null;
            }
            LinkProperties linkProperties = this.connectivityManager.getLinkProperties(network);
            if (linkProperties == null) {
                Logging.w(NetworkMonitorAutoDetect.TAG, "Detected unknown network: " + network.toString());
                return null;
            } else if (linkProperties.getInterfaceName() == null) {
                Logging.w(NetworkMonitorAutoDetect.TAG, "Null interface name for network " + network.toString());
                return null;
            } else {
                NetworkState networkState = getNetworkState(network);
                ConnectionType connectionType = NetworkMonitorAutoDetect.getConnectionType(networkState);
                if (connectionType == ConnectionType.CONNECTION_NONE) {
                    Logging.d(NetworkMonitorAutoDetect.TAG, "Network " + network.toString() + " is disconnected");
                    return null;
                }
                if (connectionType == ConnectionType.CONNECTION_UNKNOWN || connectionType == ConnectionType.CONNECTION_UNKNOWN_CELLULAR) {
                    Logging.d(NetworkMonitorAutoDetect.TAG, "Network " + network.toString() + " connection type is " + connectionType + " because it has type " + networkState.getNetworkType() + " and subtype " + networkState.getNetworkSubType());
                }
                ConnectionType underlyingConnectionTypeForVpn = NetworkMonitorAutoDetect.getUnderlyingConnectionTypeForVpn(networkState);
                NetworkInformation networkInformation = new NetworkInformation(linkProperties.getInterfaceName(), connectionType, underlyingConnectionTypeForVpn, NetworkMonitorAutoDetect.networkToNetId(network), getIPAddresses(linkProperties));
                return networkInformation;
            }
        }

        @SuppressLint({"NewApi"})
        boolean hasInternetCapability(Network network) {
            NetworkCapabilities capabilities;
            return (this.connectivityManager == null || (capabilities = this.connectivityManager.getNetworkCapabilities(network)) == null || !capabilities.hasCapability(12)) ? false : true;
        }

        @SuppressLint({"NewApi"})
        public void registerNetworkCallback(ConnectivityManager.NetworkCallback networkCallback) {
            this.connectivityManager.registerNetworkCallback(new NetworkRequest.Builder().addCapability(12).build(), networkCallback);
        }

        @SuppressLint({"NewApi"})
        public void requestMobileNetwork(ConnectivityManager.NetworkCallback networkCallback) {
            NetworkRequest.Builder builder = new NetworkRequest.Builder();
            builder.addCapability(12).addTransportType(0);
            this.connectivityManager.requestNetwork(builder.build(), networkCallback);
        }

        @SuppressLint({"NewApi"})
        IPAddress[] getIPAddresses(LinkProperties linkProperties) {
            IPAddress[] ipAddresses = new IPAddress[linkProperties.getLinkAddresses().size()];
            int i = 0;
            for (LinkAddress linkAddress : linkProperties.getLinkAddresses()) {
                ipAddresses[i] = new IPAddress(linkAddress.getAddress().getAddress());
                i++;
            }
            return ipAddresses;
        }

        @SuppressLint({"NewApi"})
        public void releaseCallback(ConnectivityManager.NetworkCallback networkCallback) {
            if (supportNetworkCallback()) {
                Logging.d(NetworkMonitorAutoDetect.TAG, "Unregister network callback");
                this.connectivityManager.unregisterNetworkCallback(networkCallback);
            }
        }

        public boolean supportNetworkCallback() {
            return Build.VERSION.SDK_INT >= 21 && this.connectivityManager != null;
        }
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    /* loaded from: input.aar:classes.jar:org/webrtc/NetworkMonitorAutoDetect$WifiManagerDelegate.class */
    public static class WifiManagerDelegate {
        @Nullable
        private final Context context;

        WifiManagerDelegate(Context context) {
            this.context = context;
        }

        WifiManagerDelegate() {
            this.context = null;
        }

        String getWifiSSID() {
            WifiInfo wifiInfo;
            String ssid;
            Intent intent = this.context.registerReceiver(null, new IntentFilter("android.net.wifi.STATE_CHANGE"));
            if (intent != null && (wifiInfo = (WifiInfo) intent.getParcelableExtra("wifiInfo")) != null && (ssid = wifiInfo.getSSID()) != null) {
                return ssid;
            }
            return "";
        }
    }

    /* loaded from: input.aar:classes.jar:org/webrtc/NetworkMonitorAutoDetect$WifiDirectManagerDelegate.class */
    static class WifiDirectManagerDelegate extends BroadcastReceiver {
        private static final int WIFI_P2P_NETWORK_HANDLE = 0;
        private final Context context;
        private final Observer observer;
        @Nullable
        private NetworkInformation wifiP2pNetworkInfo;

        WifiDirectManagerDelegate(Observer observer, Context context) {
            this.context = context;
            this.observer = observer;
            IntentFilter intentFilter = new IntentFilter();
            intentFilter.addAction("android.net.wifi.p2p.STATE_CHANGED");
            intentFilter.addAction("android.net.wifi.p2p.CONNECTION_STATE_CHANGE");
            context.registerReceiver(this, intentFilter);
            if (Build.VERSION.SDK_INT > 28) {
                WifiP2pManager manager = (WifiP2pManager) context.getSystemService("wifip2p");
                WifiP2pManager.Channel channel = manager.initialize(context, context.getMainLooper(), null);
                manager.requestGroupInfo(channel, wifiP2pGroup -> {
                    onWifiP2pGroupChange(wifiP2pGroup);
                });
            }
        }

        @Override // android.content.BroadcastReceiver
        @SuppressLint({"InlinedApi"})
        public void onReceive(Context context, Intent intent) {
            if ("android.net.wifi.p2p.CONNECTION_STATE_CHANGE".equals(intent.getAction())) {
                WifiP2pGroup wifiP2pGroup = (WifiP2pGroup) intent.getParcelableExtra("p2pGroupInfo");
                onWifiP2pGroupChange(wifiP2pGroup);
            } else if ("android.net.wifi.p2p.STATE_CHANGED".equals(intent.getAction())) {
                int state = intent.getIntExtra("wifi_p2p_state", WIFI_P2P_NETWORK_HANDLE);
                onWifiP2pStateChange(state);
            }
        }

        public void release() {
            this.context.unregisterReceiver(this);
        }

        public List<NetworkInformation> getActiveNetworkList() {
            if (this.wifiP2pNetworkInfo != null) {
                return Collections.singletonList(this.wifiP2pNetworkInfo);
            }
            return Collections.emptyList();
        }

        private void onWifiP2pGroupChange(@Nullable WifiP2pGroup wifiP2pGroup) {
            if (wifiP2pGroup == null || wifiP2pGroup.getInterface() == null) {
                return;
            }
            try {
                NetworkInterface wifiP2pInterface = NetworkInterface.getByName(wifiP2pGroup.getInterface());
                List<InetAddress> interfaceAddresses = Collections.list(wifiP2pInterface.getInetAddresses());
                IPAddress[] ipAddresses = new IPAddress[interfaceAddresses.size()];
                for (int i = WIFI_P2P_NETWORK_HANDLE; i < interfaceAddresses.size(); i++) {
                    ipAddresses[i] = new IPAddress(interfaceAddresses.get(i).getAddress());
                }
                this.wifiP2pNetworkInfo = new NetworkInformation(wifiP2pGroup.getInterface(), ConnectionType.CONNECTION_WIFI, ConnectionType.CONNECTION_NONE, 0L, ipAddresses);
                this.observer.onNetworkConnect(this.wifiP2pNetworkInfo);
            } catch (SocketException e) {
                Logging.e(NetworkMonitorAutoDetect.TAG, "Unable to get WifiP2p network interface", e);
            }
        }

        private void onWifiP2pStateChange(int state) {
            if (state == 1) {
                this.wifiP2pNetworkInfo = null;
                this.observer.onNetworkDisconnect(0L);
            }
        }
    }

    @SuppressLint({"NewApi"})
    public NetworkMonitorAutoDetect(Observer observer, Context context) {
        this.observer = observer;
        this.context = context;
        this.connectivityManagerDelegate = new ConnectivityManagerDelegate(context);
        this.wifiManagerDelegate = new WifiManagerDelegate(context);
        NetworkState networkState = this.connectivityManagerDelegate.getNetworkState();
        this.connectionType = getConnectionType(networkState);
        this.wifiSSID = getWifiSSID(networkState);
        this.intentFilter = new IntentFilter("android.net.conn.CONNECTIVITY_CHANGE");
        if (PeerConnectionFactory.fieldTrialsFindFullName("IncludeWifiDirect").equals(PeerConnectionFactory.TRIAL_ENABLED)) {
            this.wifiDirectManagerDelegate = new WifiDirectManagerDelegate(observer, context);
        }
        registerReceiver();
        if (this.connectivityManagerDelegate.supportNetworkCallback()) {
            ConnectivityManager.NetworkCallback tempNetworkCallback = new ConnectivityManager.NetworkCallback();
            try {
                this.connectivityManagerDelegate.requestMobileNetwork(tempNetworkCallback);
            } catch (SecurityException e) {
                Logging.w(TAG, "Unable to obtain permission to request a cellular network.");
                tempNetworkCallback = null;
            }
            this.mobileNetworkCallback = tempNetworkCallback;
            this.allNetworkCallback = new SimpleNetworkCallback();
            this.connectivityManagerDelegate.registerNetworkCallback(this.allNetworkCallback);
            return;
        }
        this.mobileNetworkCallback = null;
        this.allNetworkCallback = null;
    }

    public boolean supportNetworkCallback() {
        return this.connectivityManagerDelegate.supportNetworkCallback();
    }

    void setConnectivityManagerDelegateForTests(ConnectivityManagerDelegate delegate) {
        this.connectivityManagerDelegate = delegate;
    }

    void setWifiManagerDelegateForTests(WifiManagerDelegate delegate) {
        this.wifiManagerDelegate = delegate;
    }

    boolean isReceiverRegisteredForTesting() {
        return this.isRegistered;
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    @Nullable
    public List<NetworkInformation> getActiveNetworkList() {
        List<NetworkInformation> connectivityManagerList = this.connectivityManagerDelegate.getActiveNetworkList();
        if (connectivityManagerList == null) {
            return null;
        }
        ArrayList<NetworkInformation> result = new ArrayList<>(connectivityManagerList);
        if (this.wifiDirectManagerDelegate != null) {
            result.addAll(this.wifiDirectManagerDelegate.getActiveNetworkList());
        }
        return result;
    }

    public void destroy() {
        if (this.allNetworkCallback != null) {
            this.connectivityManagerDelegate.releaseCallback(this.allNetworkCallback);
        }
        if (this.mobileNetworkCallback != null) {
            this.connectivityManagerDelegate.releaseCallback(this.mobileNetworkCallback);
        }
        if (this.wifiDirectManagerDelegate != null) {
            this.wifiDirectManagerDelegate.release();
        }
        unregisterReceiver();
    }

    private void registerReceiver() {
        if (this.isRegistered) {
            return;
        }
        this.isRegistered = true;
        this.context.registerReceiver(this, this.intentFilter);
    }

    private void unregisterReceiver() {
        if (!this.isRegistered) {
            return;
        }
        this.isRegistered = false;
        this.context.unregisterReceiver(this);
    }

    public NetworkState getCurrentNetworkState() {
        return this.connectivityManagerDelegate.getNetworkState();
    }

    public long getDefaultNetId() {
        return this.connectivityManagerDelegate.getDefaultNetId();
    }

    private static ConnectionType getConnectionType(boolean isConnected, int networkType, int networkSubtype) {
        if (!isConnected) {
            return ConnectionType.CONNECTION_NONE;
        }
        switch (networkType) {
            case 0:
                switch (networkSubtype) {
                    case 1:
                    case WebRtcAudioRecord.DEFAULT_AUDIO_FORMAT /* 2 */:
                    case EglBase.EGL_OPENGL_ES2_BIT /* 4 */:
                    case WebRtcAudioRecord.DEFAULT_AUDIO_SOURCE /* 7 */:
                    case 11:
                        return ConnectionType.CONNECTION_2G;
                    case 3:
                    case 5:
                    case 6:
                    case 8:
                    case 9:
                    case 10:
                    case 12:
                    case 14:
                    case 15:
                        return ConnectionType.CONNECTION_3G;
                    case 13:
                        return ConnectionType.CONNECTION_4G;
                    default:
                        return ConnectionType.CONNECTION_UNKNOWN_CELLULAR;
                }
            case 1:
                return ConnectionType.CONNECTION_WIFI;
            case WebRtcAudioRecord.DEFAULT_AUDIO_FORMAT /* 2 */:
            case 3:
            case EglBase.EGL_OPENGL_ES2_BIT /* 4 */:
            case 5:
            case 8:
            case 10:
            case 11:
            case 12:
            case 13:
            case 14:
            case 15:
            case 16:
            default:
                return ConnectionType.CONNECTION_UNKNOWN;
            case 6:
                return ConnectionType.CONNECTION_4G;
            case WebRtcAudioRecord.DEFAULT_AUDIO_SOURCE /* 7 */:
                return ConnectionType.CONNECTION_BLUETOOTH;
            case 9:
                return ConnectionType.CONNECTION_ETHERNET;
            case 17:
                return ConnectionType.CONNECTION_VPN;
        }
    }

    public static ConnectionType getConnectionType(NetworkState networkState) {
        return getConnectionType(networkState.isConnected(), networkState.getNetworkType(), networkState.getNetworkSubType());
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static ConnectionType getUnderlyingConnectionTypeForVpn(NetworkState networkState) {
        if (networkState.getNetworkType() != 17) {
            return ConnectionType.CONNECTION_NONE;
        }
        return getConnectionType(networkState.isConnected(), networkState.getUnderlyingNetworkTypeForVpn(), networkState.getUnderlyingNetworkSubtypeForVpn());
    }

    private String getWifiSSID(NetworkState networkState) {
        if (getConnectionType(networkState) != ConnectionType.CONNECTION_WIFI) {
            return "";
        }
        return this.wifiManagerDelegate.getWifiSSID();
    }

    @Override // android.content.BroadcastReceiver
    public void onReceive(Context context, Intent intent) {
        NetworkState networkState = getCurrentNetworkState();
        if ("android.net.conn.CONNECTIVITY_CHANGE".equals(intent.getAction())) {
            connectionTypeChanged(networkState);
        }
    }

    private void connectionTypeChanged(NetworkState networkState) {
        ConnectionType newConnectionType = getConnectionType(networkState);
        String newWifiSSID = getWifiSSID(networkState);
        if (newConnectionType == this.connectionType && newWifiSSID.equals(this.wifiSSID)) {
            return;
        }
        this.connectionType = newConnectionType;
        this.wifiSSID = newWifiSSID;
        Logging.d(TAG, "Network connectivity changed, type is: " + this.connectionType);
        this.observer.onConnectionTypeChanged(newConnectionType);
    }

    /* JADX INFO: Access modifiers changed from: private */
    @SuppressLint({"NewApi"})
    public static long networkToNetId(Network network) {
        if (Build.VERSION.SDK_INT >= 23) {
            return network.getNetworkHandle();
        }
        return Integer.parseInt(network.toString());
    }
}
