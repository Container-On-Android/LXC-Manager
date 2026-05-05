package io.github.coap.lxc;

import android.content.Intent;
import android.os.IBinder;
import android.util.Log;
import androidx.annotation.NonNull;
import com.topjohnwu.superuser.ipc.RootService;
import io.github.coap.IService;

public class LxcNative extends RootService {
    public static String LXC_PATH = "/data/share/var/lib/lxc";

    static {
        System.loadLibrary("lxc-binding");
    }

    public static String getVersion() {
        return nativeGetVersion();
    }

    @SuppressWarnings({"JniMissingFunction", "unused"})
    static class LxcIPC extends IService.Stub {
        @Override
        public int getUid() {
            return 0;
        }

        @Override
        public String[] listContainers(String lxcpath) {
            return nativeListContainers(lxcpath);
        }

        @Override
        public boolean isDefined(String name, String lxcpath) {
            return nativeIsDefined(name, lxcpath);
        }

        @Override
        public boolean isRunning(String name, String lxcpath) {
            return nativeIsRunning(name, lxcpath);
        }

        @Override
        public String getState(String name, String lxcpath) {
            return nativeGetState(name, lxcpath);
        }

        @Override
        public boolean startContainer(String name, String lxcpath, boolean useInit) {
            return nativeStartContainer(name, lxcpath, useInit);
        }

        @Override
        public boolean stopContainer(String name, String lxcpath) {
            return nativeStopContainer(name, lxcpath);
        }

        @Override
        public boolean freezeContainer(String name, String lxcpath) {
            return nativeFreezeContainer(name, lxcpath);
        }

        @Override
        public boolean unfreezeContainer(String name, String lxcpath) {
            return nativeUnfreezeContainer(name, lxcpath);
        }

        @Override
        public boolean destroyContainer(String name, String lxcpath) {
            return nativeDestroyContainer(name, lxcpath);
        }

        @Override
        public String getConfigItem(String name, String lxcpath, String key) {
            return nativeGetConfigItem(name, lxcpath, key);
        }

        @Override
        public boolean setConfigItem(String name, String lxcpath, String key, String value) {
            return nativeSetConfigItem(name, lxcpath, key, value);
        }

        @Override
        public int createSnapshot(String name, String lxcpath) {
            return nativeCreateSnapshot(name, lxcpath);
        }

        @Override
        public String[] getInterfaces(String name, String lxcpath) {
            return nativeGetInterfaces(name, lxcpath);
        }

        @Override
        public int getConsoleFd(String name, String lxcpath, int ttynum) {
            return nativeGetConsoleFd(name, lxcpath, ttynum);
        }
    
        @Override
        public boolean console(String name, String lxcpath, int ttynum, int stdinfd, int stdoutfd, int stderrfd, int escape) {
            return nativeConsole(name, lxcpath, ttynum, stdinfd, stdoutfd, stderrfd, escape);
        }
    
        @Override
        public int attachRunWait(String name, String lxcpath, boolean clearEnv, int namespaces, long personality, int uid, int gid, String[] argv, int attachFlags) {
            return nativeAttachRunWait(name, lxcpath, clearEnv, namespaces, personality, uid, gid, argv, attachFlags);
        }
    
        @Override
        public int attachNoWait(String name, String lxcpath, boolean clearEnv, int namespaces, long personality, int uid, int gid, String[] argv, int attachFlags) {
            return nativeAttachNoWait(name, lxcpath, clearEnv, namespaces, personality, uid, gid, argv, attachFlags);
        }
    
        @Override
        public int getErrorNum(String name, String lxcpath) {
            return nativeGetErrorNum(name, lxcpath);
        }
    }

    // Native method
    @SuppressWarnings("JniMissingFunction")
    private static native String nativeGetVersion();
    private static native String[] nativeListContainers(String lxcpath);
    private static native boolean nativeIsDefined(String name, String lxcpath);
    private static native boolean nativeIsRunning(String name, String lxcpath);
    private static native String nativeGetState(String name, String lxcpath);
    private static native boolean nativeStartContainer(String name, String lxcpath, boolean useInit);
    private static native boolean nativeStopContainer(String name, String lxcpath);
    private static native boolean nativeFreezeContainer(String name, String lxcpath);
    private static native boolean nativeUnfreezeContainer(String name, String lxcpath);
    private static native boolean nativeDestroyContainer(String name, String lxcpath);
    private static native String nativeGetConfigItem(String name, String lxcpath, String key);
    private static native boolean nativeSetConfigItem(String name, String lxcpath, String key, String value);
    private static native int nativeCreateSnapshot(String name, String lxcpath);
    private static native String[] nativeGetInterfaces(String name, String lxcpath);
    private static native int nativeGetConsoleFd(String name, String lxcpath, int ttynum);
    private static native boolean nativeConsole(String name, String lxcpath, int ttynum, int stdinfd, int stdoutfd, int stderrfd, int escape);
    private static native int nativeAttachRunWait(String name, String lxcpath, boolean clearEnv, int namespaces, long personality, int uid, int gid, String[] argv, int attachFlags);
    private static native int nativeAttachNoWait(String name, String lxcpath, boolean clearEnv, int namespaces, long personality, int uid, int gid, String[] argv, int attachFlags);
    private static native int nativeGetErrorNum(String name, String lxcpath);
    
    @Override
    public void onCreate() {
        Log.d("LXC", "LXC Service Created");
    }

    @Override
    public void onRebind(@NonNull Intent intent) {
        super.onRebind(intent);
        Log.d("LXC", "LXC Service Rebound");
    }

    @Override
    public IBinder onBind(@NonNull Intent intent) {
        Log.d("LXC", "LXC Service Bound");
        return new LxcIPC();
    }

    @Override
    public boolean onUnbind(@NonNull Intent intent) {
        Log.d("LXC", "LXC Service Unbound");
        return true;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d("LXC", "LXC Service Destroyed");
    }
}