
package com.atakmap.android.takml;

import static android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS;

import android.Manifest;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.PowerManager;
import com.atakmap.coremap.log.Log;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;

import com.atakmap.android.takml.plugin.R;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class CameraActivity extends Activity {
    private static final int CAMERA_REQUEST = 8888;
    public static final String CAMERA_INFO = CameraActivity.class + ".CAMERA_INFO";

    // needs to be in both places
    public static final String PLUGIN_PERMISSION_REQUEST_ERROR = "com.atakmap.android.helloworld.PluginPermissionsActivity.ERROR";


    private static final String TAG = CameraActivity.class.getSimpleName();

    private final String[] permissionList =
            new String[] {
                    Manifest.permission.CAMERA
            };

    public static final int ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS_CODE = 10000;

    // the number of times a request has been made and not satisfied
    int times = 0;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (hasPermissions(permissionList)) {
            synchronized (this) {
                checkAndRequest();
                Intent cameraIntent = new Intent(
                        android.provider.MediaStore.ACTION_IMAGE_CAPTURE);
                startActivityForResult(cameraIntent, CAMERA_REQUEST);
            }
        } else {
            requestPermissions(permissionList);
        }
    }

    public void checkAndRequest() {
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (pm.isIgnoringBatteryOptimizations(getPackageName())) {
            finish();
        }

        try {
            Intent intent = new Intent(
                    ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            intent.setData(Uri.parse("package:" + getPackageName()));
            startActivityForResult(intent, ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS_CODE);
        } catch (Exception e) {
            Log.w(TAG, "Failed to disable battery optimizations.");
        }

    }


    @TargetApi(23)
    private void requestPermissions(String[] permissionList) {
        if (times < 3) {
            times++;
            requestPermissions(permissionList, 315);
        } else {
            final Activity a = this;
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setMessage(R.string.permission_warning);

            builder.setCancelable(false);
            builder.setPositiveButton(R.string.permit_manually,
                    (dialog, which) -> {
                        dialog.dismiss();
                        Intent intent = new Intent();
                        intent.setAction(
                                android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                        Uri uri = Uri.fromParts("package", a.getPackageName(),
                                null);
                        intent.setData(uri);
                        a.startActivity(intent);
                        sendBroadcast(new Intent(PLUGIN_PERMISSION_REQUEST_ERROR));
                        a.finish();
                    });
            builder.show();
        }

    }

    private boolean hasPermissions(String[] permissions) {
        for (String permission : permissions) {
            if (checkSelfPermission(permission) == PackageManager.PERMISSION_DENIED)
                return false;
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        for (int i = 0; i < grantResults.length; ++i) {
            if (grantResults[i] == PackageManager.PERMISSION_DENIED) {
                Log.e(TAG, "permission denied: " + permissions[i]);
                requestPermissions(permissionList);
                return;
            }
        }
        synchronized (this) {
            checkAndRequest();
        }
    }

    private void startCameraIntent(){
        Log.d(TAG, "Starting camera activity");
        Intent cameraIntent = new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);
        startActivityForResult(cameraIntent, CAMERA_REQUEST);
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
        Log.v(TAG, "onActivityResult");

        Intent i = new Intent(CAMERA_INFO);
        if (requestCode == CAMERA_REQUEST && resultCode == Activity.RESULT_OK) {

            Bundle extras = data.getExtras();
            if (extras != null) {
                Bitmap photo = (Bitmap) extras.get("data");

                File folder = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);

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

                i.putExtra("image", BitmapCompressionUtil.scaleBitmapForParcelable(photo));
            }
        }
        sendBroadcast(i);
        finish();

//        try {
//            if (requestCode == CAMERA_REQUEST && resultCode == Activity.RESULT_OK) {
//                Bitmap photo = (Bitmap) data.getExtras().get("data");
//                Intent i = new Intent(CAMERA_INFO);
//                i.putExtra("image", photo);
//                sendBroadcast(i);

//                File image = null;
//                FileOutputStream outStream = null;
//                boolean errors = false;
//                try {
//                    // First, try to write the image data to the DCIM directory in external storage.
//                    image = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM) + File.separator + "test.png");
//                    Log.i(TAG, "Using path: " + image.getAbsolutePath());
//                    outStream = new FileOutputStream(image);
//                    photo.compress(Bitmap.CompressFormat.PNG, 100, outStream);
//                    // 100 to keep full quality of the image
//                    outStream.flush();
//                } catch (Exception e) {
//                    // If we couldn't write the image data to the DCIM directory,
//                    // try the application-specific directory next.
//                    try {
//                        image = getPhotoFileUri("test.png");
//                        Log.i(TAG, "Falling back to path: " + image.getAbsolutePath());
//                        outStream = new FileOutputStream(image);
//                        photo.compress(Bitmap.CompressFormat.PNG, 100, outStream);
//                        // 100 to keep full quality of the image
//                        outStream.flush();
//                    } catch (Exception e1) {
//                        // If all else fails, log the error.
//                        Log.e(TAG, "Unable to load image", e1);
//                        errors = true;
//                    }
//                } finally {
//                    if (outStream != null) {
//                        outStream.close();
//                    }
//                }
//
//                if (!errors) {
//                    Log.i(TAG, "Saved image to path: " + image.getAbsolutePath());
//                    Intent i = new Intent(CAMERA_INFO);
//                    i.putExtra("image", image.getAbsolutePath());
//                    sendBroadcast(i);
//                }
//            } else {
//                Log.d(TAG, "Failed to get image: " + requestCode + ", " + resultCode);
//            }
//        } catch (Exception e) {
//            // Log an error if the operation fails for some other reason.
//            Log.e(TAG, "Unable to write image", e);
//        }
//        finish();
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
