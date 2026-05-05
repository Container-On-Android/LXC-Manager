package io.github.coap;

interface IService {
    int getUid();

    // Container
    String[] listContainers(String lxcpath);
    boolean isDefined(String name, String lxcpath);
    boolean isRunning(String name, String lxcpath);
    String getState(String name, String lxcpath);
    boolean startContainer(String name, String lxcpath, boolean useInit);
    boolean stopContainer(String name, String lxcpath);
    boolean freezeContainer(String name, String lxcpath);
    boolean unfreezeContainer(String name, String lxcpath);
    boolean destroyContainer(String name, String lxcpath);
    int createSnapshot(String name, String lxcpath);
    String getConfigItem(String name, String lxcpath, String key);
    boolean setConfigItem(String name, String lxcpath, String key, String value);
    String[] getInterfaces(String name, String lxcpath);
    int getConsoleFd(String name, String lxcpath, int ttynum);
    boolean console(String name, String lxcpath, int ttynum, int stdinfd, int stdoutfd, int stderrfd, int escape);
    int attachRunWait(String name, String lxcpath, boolean clearEnv, int namespaces,
                        long personality, int uid, int gid, in String[] argv, int attachFlags);
    int attachNoWait(String name, String lxcpath, boolean clearEnv, int namespaces,
                        long personality, int uid, int gid, in String[] argv, int attachFlags);
    int getErrorNum(String name, String lxcpath);
}