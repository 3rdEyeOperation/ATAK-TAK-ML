
package com.atakmap.android.takml.plugin;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import com.atakmap.coremap.log.Log;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.atakmap.android.takml.BitmapCompressionUtil;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

public class LoadFileActivity extends Activity {
    private static final int LOAD_FILE_REQUEST = 8888;
    public static final String CAMERA_INFO = LoadFileActivity.class + ".CAMERA_INFO";
    private static final String TAG = LoadFileActivity.class.getSimpleName();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        startLoadFileIntent();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        for (int i = 0 ; i < grantResults.length; i++) {
            if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, permissions, 0);
                return;
            }
        }
    }

    private void startLoadFileIntent() {

        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");

        // Do this if you need to be able to open the returned URI as a stream
        // (for example here to read the image data).
        intent.addCategory(Intent.CATEGORY_OPENABLE);

        startActivityForResult(intent, LOAD_FILE_REQUEST);
    }

    public File getPhotoFileUri(String fileName) {
        // Get safe storage directory for photos
        // Use `getExternalFilesDir` on Context to access package-specific directories.
        // This way, we don't need to request external read/write runtime permissions.
        File mediaStorageDir = new File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), TAG);

        // Create the storage directory if it does not exist
        if (!mediaStorageDir.exists() && !mediaStorageDir.mkdirs()){
            Log.d(TAG, "failed to create directory");
        }

        // Return the file target for the photo based on filename
        File file = new File(mediaStorageDir.getPath() + File.separator + fileName);

        return file;
    }

    protected void onActivityResult(int requestCode, int resultCode,
                                    Intent data) {
        Log.d(TAG, "onActivityResult");

        try {
            if (requestCode == LOAD_FILE_REQUEST && resultCode == Activity.RESULT_OK) {
                Log.d(TAG, "Got result of file request.");

                File folder = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
                Bitmap photo = BitmapFactory.decodeStream(getContentResolver().openInputStream(data.getData()));

                File file = new File(folder, "test.png");
                FileOutputStream fileOutputStream = null;
                try {
                    fileOutputStream = new FileOutputStream(file);
                    ByteArrayOutputStream stream = new ByteArrayOutputStream();
                    photo.compress(Bitmap.CompressFormat.PNG, 100, stream);
                    byte[] byteArray = stream.toByteArray();
                    fileOutputStream.write(byteArray);
                } catch (Exception e) {
                    Log.e(TAG, "Could not save image", e);
                } finally {
                    if (fileOutputStream != null) {
                        try {
                            fileOutputStream.close();
                        } catch (IOException e) {
                            Log.e(TAG, "Could not save image", e);
                        }
                    }
                }

                Intent i = new Intent(CAMERA_INFO);
                i.putExtra("image", BitmapCompressionUtil.scaleBitmapForParcelable(photo));
                sendBroadcast(i);
            } else {
                Log.d(TAG, "Failed to get image: " + requestCode + ", " + resultCode);
            }

        } catch (Exception e) {
            // Log an error if the operation fails for some other reason.
            Log.e(TAG, "Unable to write image", e);
        }
        finish();
    }

    public interface CameraDataReceiver {
        void onCameraDataReceived(Bitmap bitmap);
    }

    /**
     * Broadcast Receiver that is responsible for getting the data back to the
     * plugin.
     */
    public static class CameraDataListener extends BroadcastReceiver {
        private boolean registered = false;
        private CameraDataReceiver cdr = null;

        @SuppressLint("UnspecifiedRegisterReceiverFlag")
        synchronized public void register(Context context,
                                          CameraDataReceiver cdr) {
            if (!registered){
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    context.registerReceiver(this, new IntentFilter(CAMERA_INFO), RECEIVER_EXPORTED);
                }else {
                    context.registerReceiver(this, new IntentFilter(CAMERA_INFO));
                }
            }

            this.cdr = cdr;
            registered = true;
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            synchronized (this) {
                try {
                    Bundle extras = intent.getExtras();
                    if (extras != null) {
                        Bitmap bitmap = (Bitmap) extras.get("image");
                        if (bitmap != null && cdr != null)
                            cdr.onCameraDataReceived(bitmap);
                    }
                } catch (Exception ignored) {
                }
                if (registered) {
                    context.unregisterReceiver(this);
                    registered = false;
                }
            }
        }

    }
}