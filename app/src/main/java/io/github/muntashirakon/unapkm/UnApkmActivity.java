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

import android.content.ContentResolver;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.widget.Toast;

import com.souramoo.unapkm.UnApkm;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

public class UnApkmActivity extends AppCompatActivity {
    private ActivityResultLauncher<String> exportManifest = registerForActivityResult(
            new ActivityResultContracts.CreateDocument(),
            uri -> {
                if (uri == null) {
                    // Back button pressed.
                    return;
                }
                try {
                    new UnApkmThread(uri).start();
                    new AlertDialog.Builder(this)
                            .setTitle(R.string.app_name)
                            .setCancelable(false)
                            .setView(getLayoutInflater().inflate(R.layout.dialog_progress, null))
                            .show();
                } catch (IOException e) {
                    e.printStackTrace();
                    Toast.makeText(this, R.string.failed, Toast.LENGTH_SHORT).show();
                    finish();
                }
            });
    private InputStream inputStream;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent intent = getIntent();
        // Check if action is matched
        if (intent == null || !Intent.ACTION_VIEW.equals(intent.getAction())) {
            finish();
            return;
        }
        // Read Uri
        Uri uri = intent.getData();
        if (uri == null) {
            finish();
            return;
        }
        // Open input stream
        try {
            String fileName = getFileName(getContentResolver(), uri);
            inputStream = getContentResolver().openInputStream(uri);
            if (inputStream == null) finish();
            exportManifest.launch(fileName != null ? trimExtension(fileName) + ".apks" : null);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            Toast.makeText(this, R.string.failed, Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    @Nullable
    public static String getFileName(@NonNull ContentResolver resolver, Uri uri) {
        Cursor returnCursor =
                resolver.query(uri, null, null, null, null);
        if (returnCursor == null) return null;
        int nameIndex = returnCursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
        returnCursor.moveToFirst();
        String name = returnCursor.getString(nameIndex);
        returnCursor.close();
        return name;
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
        OutputStream outputStream;
        UnApkmThread(Uri uri) throws FileNotFoundException {
            outputStream = getContentResolver().openOutputStream(uri);
        }

        @Override
        public void run() {
            UnApkm.decryptFile(inputStream, outputStream);
            runOnUiThread(() -> {
                Toast.makeText(UnApkmActivity.this, R.string.success, Toast.LENGTH_SHORT).show();
                finish();
            });
        }
    }
}
