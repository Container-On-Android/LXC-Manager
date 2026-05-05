package io.github.coap.lxc;

import android.os.RemoteException;
import android.util.Log;

import androidx.annotation.NonNull;

import io.github.coap.IService;

/**
 * LXC Container management class, provides object-oriented container operation API
 * Executes LXC operations through RootService's AIDL interface
 */
public class LxcContainer {
    private static final String TAG = "LxcContainer";
    
    private final String name;
    private final String lxcPath;
    private final IService service;
    
    /**
     * Constructor
     * @param name Container name
     * @param lxcPath LXC path
     * @param service AIDL service interface
     */
    public LxcContainer(String name, String lxcPath, IService service) {
        this.name = name;
        this.lxcPath = lxcPath;
        this.service = service;
    }
    
    /**
     * Get container name
     */
    public String getName() {
        return name;
    }
    
    /**
     * Get LXC path
     */
    public String getLxcPath() {
        return lxcPath;
    }
    
    /**
     * Check if container is defined
     */
    public boolean isDefined() {
        try {
            return service.isDefined(name, lxcPath);
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to check if container is defined: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Check if container is running
     */
    public boolean isRunning() {
        try {
            return service.isRunning(name, lxcPath);
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to check if container is running: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Get container state
     */
    public String getState() {
        try {
            return service.getState(name, lxcPath);
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to get container state: " + e.getMessage());
            return "UNKNOWN";
        }
    }
    
    /**
     * Start container
     * @param useInit Whether to use init process
     */
    public boolean start(boolean useInit) {
        try {
            return service.startContainer(name, lxcPath, useInit);
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to start container: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Start container (default not using init process)
     */
    public boolean start() {
        return start(false);
    }
    
    /**
     * Stop container
     */
    public boolean stop() {
        try {
            return service.stopContainer(name, lxcPath);
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to stop container: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Freeze container
     */
    public boolean freeze() {
        try {
            return service.freezeContainer(name, lxcPath);
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to freeze container: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Unfreeze container
     */
    public boolean unfreeze() {
        try {
            return service.unfreezeContainer(name, lxcPath);
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to unfreeze container: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Destroy container
     */
    public boolean destroy() {
        try {
            return service.destroyContainer(name, lxcPath);
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to destroy container: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Get configuration item
     * @param key Configuration key
     */
    public String getConfigItem(String key) {
        try {
            return service.getConfigItem(name, lxcPath, key);
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to get config item: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Set configuration item
     * @param key Configuration key
     * @param value Configuration value
     */
    public boolean setConfigItem(String key, String value) {
        try {
            return service.setConfigItem(name, lxcPath, key, value);
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to set config item: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Create snapshot
     */
    public int createSnapshot() {
        try {
            return service.createSnapshot(name, lxcPath);
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to create snapshot: " + e.getMessage());
            return -1;
        }
    }
    
    /**
     * Get network interfaces list
     */
    public String[] getInterfaces() {
        try {
            return service.getInterfaces(name, lxcPath);
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to get interfaces: " + e.getMessage());
            return new String[0];
        }
    }
    
    @NonNull
    @Override
    public String toString() {
        return String.format("LxcContainer{name='%s', path='%s', state='%s'}", 
                           name, lxcPath, getState());
    }
}