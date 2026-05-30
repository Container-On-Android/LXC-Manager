package io.dreamconnected.coa.lxcmanager;

interface IDecompressCallback {
    void onProgress(int progress);
    void onComplete(boolean success);
}