/*
 * Copyright (C) 2020 Muntashir Al-Islam
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.github.muntashirakon.unapkm;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.os.SystemClock;
import android.util.Log;

import com.souramoo.unapkm.UnApkm;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import io.github.muntashirakon.unapkm.api.IUnApkmService;

public class UnApkmService extends Service {
    private static final String TAG = "UnApkmService";

    private final Map<Long, ParcelFileDescriptor> mOutputPipeMap = new HashMap<>();

    private final Binder binder = new IUnApkmService.Stub() {
        @Override
        public ParcelFileDescriptor createOutputPipe(int pipeId) throws RemoteException {
            try {
                ParcelFileDescriptor[] pipe = ParcelFileDescriptor.createPipe();
                mOutputPipeMap.put(createKey(pipeId), pipe[1]);
                return pipe[0];
            } catch (IOException e) {
                Log.e(TAG, "IOException in during pipe creation.", e);
                throw new RemoteException(e.getMessage());
            }
        }

        @Override
        public void unApkm(ParcelFileDescriptor input, int pipeId, boolean cacheInput) throws RemoteException {
            long key = createKey(pipeId);
            ParcelFileDescriptor output = mOutputPipeMap.get(key);
            mOutputPipeMap.remove(key);
            if (output == null) {
                throw new RemoteException("Output pipe doesn't exist for " + pipeId);
            }
            if (input == null) {
                throw new RemoteException("Input is null for id " + pipeId);
            }
            OutputStream outputStream = new ParcelFileDescriptor.AutoCloseOutputStream(output);
            InputStream inputStream = new ParcelFileDescriptor.AutoCloseInputStream(input);
            try {
                long startTime = SystemClock.elapsedRealtime();
                if (cacheInput) {
                    // Useful if the supplied input is piped
                    File file = File.createTempFile("apkm", ".apkm", getExternalCacheDir());
                    try (FileOutputStream fos = new FileOutputStream(file)) {
                        copy(inputStream, fos);
                    }
                    try (FileInputStream fis = new FileInputStream(file)) {
                        UnApkm.decryptFile(fis, outputStream);
                    }
                } else UnApkm.decryptFile(inputStream, outputStream);
                long elapsedTime = SystemClock.elapsedRealtime() - startTime;
                Log.i(TAG, "Elapsed time: " + elapsedTime);
            } catch (Exception e) {
                Log.e(TAG, "IOException in during conversion.", e);
                throw new RemoteException(e.getMessage());
            } finally {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    Log.e(TAG, "IOException when closing input ParcelFileDescriptor", e);
                }
                try {
                    outputStream.close();
                } catch (IOException e) {
                    Log.e(TAG,"IOException when closing output ParcelFileDescriptor", e);
                }
            }
        }
    };

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    private long createKey(int id) {
        int callingPid = Binder.getCallingPid();
        return ((long) callingPid << 32) | ((long) id & 0xFFFFFFFL);
    }

    private static final int DEFAULT_BUFFER_SIZE = 1024 * 4;

    private static long copy(@NonNull InputStream inputStream, OutputStream outputStream) throws IOException {
        byte[] buffer = new byte[DEFAULT_BUFFER_SIZE];
        long count = 0;
        int n;
        while (-1 != (n = inputStream.read(buffer))) {
            outputStream.write(buffer, 0, n);
            count += n;
        }
        return count;
    }
}
