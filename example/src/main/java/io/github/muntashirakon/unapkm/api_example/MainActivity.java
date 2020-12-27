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

package io.github.muntashirakon.unapkm.api_example;

import android.annotation.SuppressLint;
import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.provider.OpenableColumns;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import io.github.muntashirakon.unapkm.api.UnApkm;

public class MainActivity extends AppCompatActivity {
    private static final String UN_APKM_PKG = "io.github.muntashirakon.unapkm";

    private ParcelFileDescriptor descriptor;
    private AlertDialog dialog;

    private final ActivityResultLauncher<String> convertLauncher = registerForActivityResult(
            new ActivityResultContracts.CreateDocument(),
            uri -> {
                if (uri == null) {
                    // Back button pressed.
                    Toast.makeText(this, "Operation cancelled.", Toast.LENGTH_SHORT).show();
                    return;
                }
                new UnApkmThread(uri).start();
            });
    private final ActivityResultLauncher<String> openUnApkm = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            uri -> {
                if (uri == null) {
                    // Back button pressed.
                    Toast.makeText(this, "Operation cancelled.", Toast.LENGTH_SHORT).show();
                    return;
                }
                // Open input stream
                try {
                    String fileName = getFileName(getContentResolver(), uri);
                    if (fileName != null && !fileName.endsWith(".apkm")) {
                        throw new IOException("Invalid file.");
                    }
                    if (descriptor != null) {
                        try {
                            descriptor.close();
                        } catch (IOException ignore) {
                        }
                    }
                    descriptor = getContentResolver().openFileDescriptor(uri, "r");
                    if (descriptor == null) throw new IOException();
                    convertLauncher.launch(fileName != null ? trimExtension(fileName) + ".apks" : null);
                } catch (IOException e) {
                    e.printStackTrace();
                    Toast.makeText(this, "Conversion failed.", Toast.LENGTH_SHORT).show();
                }
            });

    @SuppressLint("InflateParams")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.app_name)
                .setCancelable(false)
                .setView(getLayoutInflater().inflate(R.layout.dialog_progress, null))
                .create();
        findViewById(R.id.convert_btn).setOnClickListener(v -> openUnApkm.launch("application/*"));

    }

    @Override
    protected void onDestroy() {
        if (dialog != null) dialog.dismiss();
        if (descriptor != null) {
            try {
                descriptor.close();
            } catch (IOException ignore) {
            }
        }
        super.onDestroy();
    }

    @Nullable
    public static String getFileName(@NonNull ContentResolver resolver, @NonNull Uri uri) {
        if (uri.getScheme() == null) return null;
        switch (uri.getScheme()) {
            case ContentResolver.SCHEME_CONTENT:
                Cursor cursor = resolver.query(uri, null, null, null, null);
                if (cursor == null) return null;
                int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                cursor.moveToFirst();
                String name = cursor.getString(nameIndex);
                cursor.close();
                return name;
            case ContentResolver.SCHEME_FILE:
                if (uri.getPath() == null) return null;
                return new File(uri.getPath()).getName();
            default:
                return null;
        }
    }

    @NonNull
    public static String trimExtension(@NonNull String filename) {
        try {
            return filename.substring(0, filename.lastIndexOf('.'));
        } catch (Exception e) {
            return filename;
        }
    }

    class UnApkmThread extends Thread {
        Uri uri;

        UnApkmThread(Uri uri) {
            this.uri = uri;
        }

        @Override
        public void run() {
            runOnUiThread(() -> dialog.show());
            try (OutputStream outputStream = getContentResolver().openOutputStream(uri)) {
                if (outputStream == null) throw new IOException();
                UnApkm unApkm = new UnApkm(MainActivity.this, UN_APKM_PKG);
                unApkm.decryptFile(descriptor, outputStream);
                descriptor.close();
                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this, "Conversion Success!", Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                });
            } catch (IOException | RemoteException e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this, "Conversion failed!", Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                });
            }
        }
    }
}