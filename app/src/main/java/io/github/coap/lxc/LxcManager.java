package io.github.coap.lxc;

import static io.github.coap.lxc.LxcNative.LXC_PATH;

import android.os.RemoteException;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

import io.github.coap.IService;

/**
 * LXC Manager, provides multi-container management functionality
 * Executes batch LXC operations through RootService's AIDL interface
 */
public record LxcManager(IService service, String defaultLxcPath) {
    private static final String TAG = "LxcManager";

    /**
     * Constructor
     *
     * @param service        AIDL service interface
     * @param defaultLxcPath Default LXC path
     */
    public LxcManager {
    }

    /**
     * Constructor (using default path)
     *
     * @param service AIDL service interface
     */
    public LxcManager(IService service) {
        this(service, LXC_PATH);
    }

    /**
     * Get LXC version
     */
    public String getVersion() {
        return LxcNative.getVersion();
    }

    /**
     * Get all container list
     *
     * @param lxcPath LXC path
     */
    public List<LxcContainer> listContainers(String lxcPath) {
        List<LxcContainer> containers = new ArrayList<>();
        try {
            String[] containerNames = service.listContainers(lxcPath);
            if (containerNames != null) {
                for (String name : containerNames) {
                    containers.add(new LxcContainer(name, lxcPath, service));
                }
            }
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to list containers: " + e.getMessage());
        }
        return containers;
    }

    /**
     * Get all container list (using default path)
     */
    public List<LxcContainer> listContainers() {
        return listContainers(defaultLxcPath);
    }

    /**
     * Get running containers list
     *
     * @param lxcPath LXC path
     */
    public List<LxcContainer> listRunningContainers(String lxcPath) {
        List<LxcContainer> runningContainers = new ArrayList<>();
        List<LxcContainer> allContainers = listContainers(lxcPath);
        for (LxcContainer container : allContainers) {
            if (container.isRunning()) {
                runningContainers.add(container);
            }
        }
        return runningContainers;
    }

    /**
     * Get running containers list (using default path)
     */
    public List<LxcContainer> listRunningContainers() {
        return listRunningContainers(defaultLxcPath);
    }

    /**
     * Get stopped containers list
     *
     * @param lxcPath LXC path
     */
    public List<LxcContainer> listStoppedContainers(String lxcPath) {
        List<LxcContainer> stoppedContainers = new ArrayList<>();
        List<LxcContainer> allContainers = listContainers(lxcPath);
        for (LxcContainer container : allContainers) {
            if (!container.isRunning()) {
                stoppedContainers.add(container);
            }
        }
        return stoppedContainers;
    }

    /**
     * Get stopped containers list (using default path)
     */
    public List<LxcContainer> listStoppedContainers() {
        return listStoppedContainers(defaultLxcPath);
    }

    /**
     * Get container by name
     *
     * @param name    Container name
     * @param lxcPath LXC path
     */
    public LxcContainer getContainer(String name, String lxcPath) {
        try {
            if (service.isDefined(name, lxcPath)) {
                return new LxcContainer(name, lxcPath, service);
            }
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to get container: " + e.getMessage());
        }
        return null;
    }

    /**
     * Get container by name (using default path)
     *
     * @param name Container name
     */
    public LxcContainer getContainer(String name) {
        return getContainer(name, defaultLxcPath);
    }

    /**
     * Create new container
     *
     * @param name     Container name
     * @param lxcPath  LXC path
     * @param template Template name
     * @param args     Template arguments
     */
    public LxcContainer createContainer(String name, String lxcPath, String template, String[] args) {
        Log.w(TAG, "Container creation not implemented yet");
        return null;
    }

    /**
     * Get default LXC path
     */
    @Override
    public String defaultLxcPath() {
        return defaultLxcPath;
    }

    /**
     * Check if LXC service is available
     */
    public boolean isServiceAvailable() {
        String version = getVersion();
        return version != null;
    }

    @Override
    public String toString() {
        return String.format("LxcManager{defaultPath='%s', version='%s'}",
                defaultLxcPath, getVersion());
    }
}