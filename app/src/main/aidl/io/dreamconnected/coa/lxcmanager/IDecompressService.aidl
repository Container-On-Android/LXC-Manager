package io.dreamconnected.coa.lxcmanager;

import io.dreamconnected.coa.lxcmanager.IDecompressCallback;

interface IDecompressService {
    void extractTarXz(String inputPath, String outputPath, IDecompressCallback callback);
}