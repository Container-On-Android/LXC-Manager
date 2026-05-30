package io.dreamconnected.coa.lxcmanager.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import com.topjohnwu.superuser.ipc.RootService
import io.dreamconnected.coa.lxcmanager.IDecompressCallback
import io.dreamconnected.coa.lxcmanager.IDecompressService
import io.dreamconnected.coa.lxcmanager.service.DecompressService
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

object DecompressHelper {

    private var decompressService: IDecompressService? = null
    private var isBound = false
    private val mainHandler = Handler(Looper.getMainLooper())

    fun extractTarXz(context: Context, inputPath: String, outputPath: String): Boolean {
        val latch = CountDownLatch(1)
        var success = false
        
        val callback = object : IDecompressCallback.Stub() {
            override fun onProgress(progress: Int) {}
            
            override fun onComplete(result: Boolean) {
                success = result
                latch.countDown()
            }
        }

        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) {
                decompressService = IDecompressService.Stub.asInterface(service)
                isBound = true
                
                try {
                    decompressService?.extractTarXz(inputPath, outputPath, callback)
                } catch (_: Exception) {
                    success = false
                    latch.countDown()
                }
            }

            override fun onServiceDisconnected(name: ComponentName) {
                decompressService = null
                isBound = false
                latch.countDown()
            }
        }

        val intent = Intent(context, DecompressService::class.java)
        
        val runnable = Runnable {
            RootService.bind(intent, connection)
        }
        
        if (Looper.myLooper() == Looper.getMainLooper()) {
            runnable.run()
        } else {
            mainHandler.post(runnable)
        }
        
        try {
            latch.await(10, TimeUnit.MINUTES)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
        
        return success
    }
}