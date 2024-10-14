package com.smat.webrtc;

import android.content.Context;
import android.os.Build.VERSION;
import androidx.annotation.Nullable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class NetworkMonitor {
   private static final String TAG = "NetworkMonitor";
   private final ArrayList<Long> nativeNetworkObservers;
   private final ArrayList<NetworkObserver> networkObservers;
   private final Object autoDetectLock;
   @Nullable
   private NetworkMonitorAutoDetect autoDetect;
   private int numObservers;
   private volatile NetworkMonitorAutoDetect.ConnectionType currentConnectionType;

   private NetworkMonitor() {
      this.autoDetectLock = new Object();
      this.nativeNetworkObservers = new ArrayList();
      this.networkObservers = new ArrayList();
      this.numObservers = 0;
      this.currentConnectionType = NetworkMonitorAutoDetect.ConnectionType.CONNECTION_UNKNOWN;
   }

   /** @deprecated */
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
      synchronized(this.autoDetectLock) {
         ++this.numObservers;
         if (this.autoDetect == null) {
            this.autoDetect = this.createAutoDetect(applicationContext);
         }

         this.currentConnectionType = NetworkMonitorAutoDetect.getConnectionType(this.autoDetect.getCurrentNetworkState());
      }
   }

   /** @deprecated */
   @Deprecated
   public void startMonitoring() {
      this.startMonitoring(ContextUtils.getApplicationContext());
   }

   @CalledByNative
   private void startMonitoring(@Nullable Context applicationContext, long nativeObserver) {
      Logging.d("NetworkMonitor", "Start monitoring with native observer " + nativeObserver);
      this.startMonitoring(applicationContext != null ? applicationContext : ContextUtils.getApplicationContext());
      synchronized(this.nativeNetworkObservers) {
         this.nativeNetworkObservers.add(nativeObserver);
      }

      this.updateObserverActiveNetworkList(nativeObserver);
      this.notifyObserversOfConnectionTypeChange(this.currentConnectionType);
   }

   public void stopMonitoring() {
      synchronized(this.autoDetectLock) {
         if (--this.numObservers == 0) {
            this.autoDetect.destroy();
            this.autoDetect = null;
         }

      }
   }

   @CalledByNative
   private void stopMonitoring(long nativeObserver) {
      Logging.d("NetworkMonitor", "Stop monitoring with native observer " + nativeObserver);
      this.stopMonitoring();
      synchronized(this.nativeNetworkObservers) {
         this.nativeNetworkObservers.remove(nativeObserver);
      }
   }

   @CalledByNative
   private boolean networkBindingSupported() {
      synchronized(this.autoDetectLock) {
         return this.autoDetect != null && this.autoDetect.supportNetworkCallback();
      }
   }

   @CalledByNative
   private static int androidSdkInt() {
      return VERSION.SDK_INT;
   }

   private NetworkMonitorAutoDetect.ConnectionType getCurrentConnectionType() {
      return this.currentConnectionType;
   }

   private long getCurrentDefaultNetId() {
      synchronized(this.autoDetectLock) {
         return this.autoDetect == null ? -1L : this.autoDetect.getDefaultNetId();
      }
   }

   private NetworkMonitorAutoDetect createAutoDetect(Context appContext) {
      return new NetworkMonitorAutoDetect(new NetworkMonitorAutoDetect.Observer() {
         public void onConnectionTypeChanged(NetworkMonitorAutoDetect.ConnectionType newConnectionType) {
            NetworkMonitor.this.updateCurrentConnectionType(newConnectionType);
         }

         public void onNetworkConnect(NetworkMonitorAutoDetect.NetworkInformation networkInfo) {
            NetworkMonitor.this.notifyObserversOfNetworkConnect(networkInfo);
         }

         public void onNetworkDisconnect(long networkHandle) {
            NetworkMonitor.this.notifyObserversOfNetworkDisconnect(networkHandle);
         }
      }, appContext);
   }

   private void updateCurrentConnectionType(NetworkMonitorAutoDetect.ConnectionType newConnectionType) {
      this.currentConnectionType = newConnectionType;
      this.notifyObserversOfConnectionTypeChange(newConnectionType);
   }

   private void notifyObserversOfConnectionTypeChange(NetworkMonitorAutoDetect.ConnectionType newConnectionType) {
      List<Long> nativeObservers = this.getNativeNetworkObserversSync();
      Iterator var3 = nativeObservers.iterator();

      while(var3.hasNext()) {
         Long nativeObserver = (Long)var3.next();
         this.nativeNotifyConnectionTypeChanged(nativeObserver);
      }

      ArrayList javaObservers;
      synchronized(this.networkObservers) {
         javaObservers = new ArrayList(this.networkObservers);
      }

      Iterator var8 = javaObservers.iterator();

      while(var8.hasNext()) {
         NetworkObserver observer = (NetworkObserver)var8.next();
         observer.onConnectionTypeChanged(newConnectionType);
      }

   }

   private void notifyObserversOfNetworkConnect(NetworkMonitorAutoDetect.NetworkInformation networkInfo) {
      List<Long> nativeObservers = this.getNativeNetworkObserversSync();
      Iterator var3 = nativeObservers.iterator();

      while(var3.hasNext()) {
         Long nativeObserver = (Long)var3.next();
         this.nativeNotifyOfNetworkConnect(nativeObserver, networkInfo);
      }

   }

   private void notifyObserversOfNetworkDisconnect(long networkHandle) {
      List<Long> nativeObservers = this.getNativeNetworkObserversSync();
      Iterator var4 = nativeObservers.iterator();

      while(var4.hasNext()) {
         Long nativeObserver = (Long)var4.next();
         this.nativeNotifyOfNetworkDisconnect(nativeObserver, networkHandle);
      }

   }

   private void updateObserverActiveNetworkList(long nativeObserver) {
      List networkInfoList;
      synchronized(this.autoDetectLock) {
         networkInfoList = this.autoDetect == null ? null : this.autoDetect.getActiveNetworkList();
      }

      if (networkInfoList != null && networkInfoList.size() != 0) {
         NetworkMonitorAutoDetect.NetworkInformation[] networkInfos = new NetworkMonitorAutoDetect.NetworkInformation[networkInfoList.size()];
         networkInfos = (NetworkMonitorAutoDetect.NetworkInformation[])networkInfoList.toArray(networkInfos);
         this.nativeNotifyOfActiveNetworkList(nativeObserver, networkInfos);
      }
   }

   private List<Long> getNativeNetworkObserversSync() {
      synchronized(this.nativeNetworkObservers) {
         return new ArrayList(this.nativeNetworkObservers);
      }
   }

   /** @deprecated */
   @Deprecated
   public static void addNetworkObserver(NetworkObserver observer) {
      getInstance().addObserver(observer);
   }

   public void addObserver(NetworkObserver observer) {
      synchronized(this.networkObservers) {
         this.networkObservers.add(observer);
      }
   }

   /** @deprecated */
   @Deprecated
   public static void removeNetworkObserver(NetworkObserver observer) {
      getInstance().removeObserver(observer);
   }

   public void removeObserver(NetworkObserver observer) {
      synchronized(this.networkObservers) {
         this.networkObservers.remove(observer);
      }
   }

   public static boolean isOnline() {
      NetworkMonitorAutoDetect.ConnectionType connectionType = getInstance().getCurrentConnectionType();
      return connectionType != NetworkMonitorAutoDetect.ConnectionType.CONNECTION_NONE;
   }

   private native void nativeNotifyConnectionTypeChanged(long var1);

   private native void nativeNotifyOfNetworkConnect(long var1, NetworkMonitorAutoDetect.NetworkInformation var3);

   private native void nativeNotifyOfNetworkDisconnect(long var1, long var3);

   private native void nativeNotifyOfActiveNetworkList(long var1, NetworkMonitorAutoDetect.NetworkInformation[] var3);

   @Nullable
   NetworkMonitorAutoDetect getNetworkMonitorAutoDetect() {
      synchronized(this.autoDetectLock) {
         return this.autoDetect;
      }
   }

   int getNumObservers() {
      synchronized(this.autoDetectLock) {
         return this.numObservers;
      }
   }

   static NetworkMonitorAutoDetect createAndSetAutoDetectForTest(Context context) {
      NetworkMonitor networkMonitor = getInstance();
      NetworkMonitorAutoDetect autoDetect = networkMonitor.createAutoDetect(context);
      return networkMonitor.autoDetect = autoDetect;
   }

   // $FF: synthetic method
   NetworkMonitor(Object x0) {
      this();
   }

   private static class InstanceHolder {
      static final NetworkMonitor instance = new NetworkMonitor();
   }

   public interface NetworkObserver {
      void onConnectionTypeChanged(NetworkMonitorAutoDetect.ConnectionType var1);
   }
}
