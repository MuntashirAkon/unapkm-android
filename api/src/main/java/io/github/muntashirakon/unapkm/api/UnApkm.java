/*
 * Copyright 2020 Muntashir Al-Islam
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.github.muntashirakon.unapkm.api;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

@WorkerThread
public class UnApkm {
    private static final String TAG = "UnApkm";
    private static final String UN_APKM_SERVICE_ACTION = "io.github.muntashirakon.unapkm.UN_APKM";

    private IUnApkmService unApkmService;
    private CountDownLatch serviceWatcher;
    private final AtomicInteger mPipeIdGen = new AtomicInteger();
    private final ServiceConnection unApkmServiceConn = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.i(TAG, "UnApkmService connected");
            unApkmService = IUnApkmService.Stub.asInterface(service);
            if (serviceWatcher != null) serviceWatcher.countDown();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.d(TAG, "UnApkmService connected");
            unApkmService = null;
        }
    };

    public UnApkm(Context context, String targetPackageName) throws RemoteException {
        initApkmService(context, targetPackageName);
    }

    public void initApkmService(@NonNull Context context, @NonNull String targetPackageName)
            throws RemoteException {
        serviceWatcher = new CountDownLatch(1);
        Intent intent = new Intent(UN_APKM_SERVICE_ACTION);
        intent.setPackage(targetPackageName);
        Log.e(TAG, "Launching UnApkmService");
        new Handler(Looper.getMainLooper()).post(() -> {
            if (!context.bindService(intent, unApkmServiceConn, Context.BIND_AUTO_CREATE)) {
                serviceWatcher.countDown();
            }
        });
        try {
            serviceWatcher.await();
        } catch (InterruptedException e) {
            throw new RemoteException(e.getMessage());
        }
        if (unApkmService == null) throw new RemoteException("UnApkmService couldn't be bound.");
    }

    public void decryptFile(@NonNull InputStream inputStream, @NonNull OutputStream outputStream, boolean cacheInput)
            throws IOException {
        ParcelFileDescriptor output = null;
        try {
            if (unApkmService == null)
                throw new RemoteException("UnApkmService couldn't be bound.");
            int outputPipeId = mPipeIdGen.incrementAndGet();
            output = unApkmService.createOutputPipe(outputPipeId);
            Thread pumpThread = ParcelFileDescriptorUtil.pipeTo(outputStream, output);
            unApkmService.unApkm(ParcelFileDescriptorUtil.pipeFrom(inputStream), outputPipeId, cacheInput);
            pumpThread.join();
        } catch (Exception e) {
            throw new IOException("Error decrypting APKM.", e);
        } finally {
            // close() is required to halt the TransferThread
            if (output != null) {
                try {
                    output.close();
                } catch (IOException e) {
                    Log.e(TAG, "IOException when closing ParcelFileDescriptor!", e);
                }
            }
        }
    }

    public void decryptFile(@NonNull ParcelFileDescriptor descriptor, @NonNull OutputStream outputStream)
            throws IOException {
        ParcelFileDescriptor output = null;
        try {
            if (unApkmService == null)
                throw new RemoteException("UnApkmService couldn't be bound.");
            int outputPipeId = mPipeIdGen.incrementAndGet();
            output = unApkmService.createOutputPipe(outputPipeId);
            Thread pumpThread = ParcelFileDescriptorUtil.pipeTo(outputStream, output);
            unApkmService.unApkm(descriptor, outputPipeId, false);
            pumpThread.join();
        } catch (Exception e) {
            throw new IOException("Error decrypting APKM.", e);
        } finally {
            // close() is required to halt the TransferThread
            if (output != null) {
                try {
                    output.close();
                } catch (IOException e) {
                    Log.e(TAG, "IOException when closing ParcelFileDescriptor!", e);
                }
            }
        }
    }
}
