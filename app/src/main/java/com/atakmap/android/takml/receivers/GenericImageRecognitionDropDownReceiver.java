
package com.atakmap.android.takml.receivers;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Environment;
import android.os.Handler;

import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.atakmap.android.maps.MapView;
import com.atakmap.android.takml.CameraActivity;
import com.atakmap.android.takml.plugin.LoadFileActivity;
import com.atakmap.android.takml.plugin.R;
import com.atakmap.android.dropdown.DropDown.OnStateListener;
import com.atakmap.android.dropdown.DropDownReceiver;

import com.atakmap.android.takml_android.MXExecuteModelCallback;
import com.atakmap.android.takml_android.ModelTypeConstants;
import com.atakmap.android.takml_android.ProcessingParams;
import com.atakmap.android.takml_android.Takml;
import com.atakmap.android.takml_android.TakmlExecutor;
import com.atakmap.android.takml_android.TakmlInitializationListener;
import com.atakmap.android.takml_android.TakmlModel;
import com.atakmap.android.takml_android.lib.TakmlInitializationException;
import com.atakmap.android.takml_android.pytorch_mx_plugin.PytorchObjectDetectionParams;
import com.atakmap.android.takml_android.takml_result.Recognition;
import com.atakmap.android.takml_android.takml_result.TakmlResult;
import com.atakmap.coremap.log.Log;
import com.google.gson.Gson;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public class GenericImageRecognitionDropDownReceiver extends DropDownReceiver implements
        OnStateListener {
    public static final String TAG = GenericImageRecognitionDropDownReceiver.class
            .getName();

    public static final String SHOW_PLUGIN = GenericImageRecognitionDropDownReceiver.class.getName() + "_SHOW_GENERIC_IMAGE_RECOGNITION_PLUGIN";

    private final static int OBJECT_DETECTION_TEXT_X = 15;
    private final static int OBJECT_DETECTION_TEXT_Y = 20;
    private final static int OBJECT_DETECTION_TEXT_WIDTH = 70;
    private final static int OBJECT_DETECTION_TEXT_HEIGHT = 25;

    private final View templateView;
    private final Context pluginContext;
    private ImageView imageView;
    private ImageButton takeNewImageButton, loadNewImageButton;
    private final AtomicReference<Bitmap> image = new AtomicReference<>();
    private TextView predictionResultsDisplay;
    private TextView confidenceResultsDisplay;
    private Handler mHandler;
    private Button settingsButton, sendButton;
    private ProgressBar pendingBar;
    private TextView header;

    private static final String WAITING_TEXT = "Waiting for result";
    private Object enableUIToken;

    private boolean havePendingResult = false;
    private boolean configured = false;
    private Takml takml;
    private TakmlExecutor takmlExecutor;

    private final CameraActivity.CameraDataListener cdl = new CameraActivity.CameraDataListener();

    private final CameraActivity.CameraDataReceiver cdr = new CameraActivity.CameraDataReceiver() {
        public void onCameraDataReceived(Bitmap bitmap) {
            Log.d(TAG, "==========img received======>");

            // Accessing the saved data from the downloads folder
            File folder = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);

            // geeksData represent the file data that is saved publicly
            File file = new File(folder, "test.png");
            byte[] bytes = new byte[(int) file.length()];

            try(FileInputStream fis = new FileInputStream(file)) {
                fis.read(bytes);
                imageView.setImageBitmap(BitmapFactory.decodeByteArray(bytes, 0, bytes.length));
            } catch (Exception e) {
                Log.e(TAG, "Could not find file, applying image from intent", e);
                imageView.setImageBitmap(bitmap);
            }
            image.set(bitmap);


        }
    };
    private final LoadFileActivity.CameraDataListener loadFileListener = new LoadFileActivity.CameraDataListener();

    private final LoadFileActivity.CameraDataReceiver loadFileReceiver = new LoadFileActivity.CameraDataReceiver() {
        @Override
        public void onCameraDataReceived(Bitmap bitmap) {
            Log.d(TAG, "==========img received======>");
            imageView.setImageBitmap(bitmap);
            image.set(bitmap);

        }
    };

    private void runPrediction() {
        Bitmap bitmap = Bitmap.createBitmap(imageView.getWidth(), imageView.getHeight(), Bitmap.Config.ARGB_8888);
        imageView.draw(new Canvas(bitmap));
        pendingBar.setVisibility(View.VISIBLE);
        predictionResultsDisplay.setText("");
        confidenceResultsDisplay.setText("");
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
        byte[] bitmapBytes = stream.toByteArray();

        Log.d(TAG, "running prediction");
        takmlExecutor.executePrediction(bitmapBytes, new MXExecuteModelCallback() {
            @Override
            public void modelResult(List<? extends TakmlResult> takmlResults, boolean success, String modelType) {
                Log.d(TAG, "Got execute reply.");
                if(takmlResults.size() == 0){
                    pendingBar.setVisibility(View.GONE);
                    predictionResultsDisplay.setText("No detected Results");
                    Log.e(TAG, "Results are null");
                    return;
                }
                if (!success) {
                    Log.e(TAG, "Could not execute request");
                    return;
                }

                Log.d(TAG, "modelResult: " + takmlResults.size());

                ProcessingParams processingParams = takmlExecutor.getSelectedModel().getProcessingParams();
                int outputWidth = 420;
                int outputHeight = 420;
                if(processingParams != null){
                    if(processingParams instanceof PytorchObjectDetectionParams){
                        PytorchObjectDetectionParams pytorchObjectDetectionParams =
                                (PytorchObjectDetectionParams) processingParams;
                        outputWidth = pytorchObjectDetectionParams.getModelInputWidth();
                        outputHeight = pytorchObjectDetectionParams.getModelInputHeight();
                    }
                }


                int finalOutputWidth = outputWidth;
                int finalOutputHeight = outputHeight;
                new Handler(Looper.getMainLooper()).post(() -> {
                    StringBuilder stringBuilder = new StringBuilder();
                    if(modelType.equals(ModelTypeConstants.IMAGE_CLASSIFICATION)) {
                        if (takmlResults.size() == 0){
                            pendingBar.setVisibility(View.GONE);
                            predictionResultsDisplay.setText("No detected Results");
                            return;
                        }
                        Recognition recognition = (Recognition) takmlResults.get(0);
                        String label = recognition.getLabel();
                        float confidenceScore = recognition.getConfidence();
                        stringBuilder.append("confidence = ").append(confidenceScore);
                        pendingBar.setVisibility(View.GONE);
                        predictionResultsDisplay.setText(label);
                        confidenceResultsDisplay.setText(stringBuilder);

                    }else{
                        Bitmap bmp2 = Bitmap.createBitmap(imageView.getWidth(), imageView.getHeight(), Bitmap.Config.ARGB_8888);
                        imageView.draw(new Canvas(bmp2));
                        Bitmap resizedBitmap = Bitmap.createScaledBitmap(bmp2, finalOutputWidth, finalOutputHeight,
                                true);
                        Canvas canvas = new Canvas(resizedBitmap);
                        if (takmlResults.size() == 0){
                            stringBuilder.append("No detected results");
                        }
                        for(TakmlResult takmlResult : takmlResults) {
                            Recognition objectDetection = (Recognition) takmlResult;
                            if(objectDetection.getConfidence() < 0.3){
                                continue;
                            }
                            Recognition recognition = (Recognition) takmlResult;
                            stringBuilder.append(recognition.getLabel())
                                    .append(", confidence = ")
                                    .append(recognition.getConfidence())
                                    .append(" ");

                            Rect rect = new Rect((int) objectDetection.getLeft(),
                                    (int) objectDetection.getTop(),
                                    (int) objectDetection.getRight(),
                                    (int) objectDetection.getBottom());

                            Paint mPaintRectangle = new Paint();
                            mPaintRectangle.setColor(Color.YELLOW);
                            mPaintRectangle.setStrokeWidth(5);
                            mPaintRectangle.setStyle(Paint.Style.STROKE);

                            Paint mPaintText = new Paint();
                            canvas.drawRect(rect, mPaintRectangle);

                            Path mPath = new Path();
                            RectF mRectF = new RectF(rect.left, rect.top, rect.left + OBJECT_DETECTION_TEXT_WIDTH, rect.top + OBJECT_DETECTION_TEXT_HEIGHT);
                            mPath.addRect(mRectF, Path.Direction.CW);
                            mPaintText.setColor(Color.MAGENTA);
                            canvas.drawPath(mPath, mPaintText);

                            mPaintText.setColor(Color.WHITE);
                            mPaintText.setStrokeWidth(0);
                            mPaintText.setStyle(Paint.Style.FILL);
                            mPaintText.setTextSize(32);
                            canvas.drawText(objectDetection.getLabel(), rect.left + OBJECT_DETECTION_TEXT_X, rect.top + OBJECT_DETECTION_TEXT_Y, mPaintText);
                        }
                        pendingBar.setVisibility(View.GONE);
                        imageView.setImageBitmap(resizedBitmap);
                        confidenceResultsDisplay.setText(stringBuilder);
                    }

                    Toast.makeText(pluginContext, stringBuilder.toString(),
                            Toast.LENGTH_SHORT).show();
                    Log.d(TAG, "Finished processing prediction result");
                });


            }
        });
    }

    private byte[] importModelFromAssets(Context context, String name){
        byte[] modelBytes = null;
        try(InputStream modelInputStream = context.getAssets().open(name)) {
            modelBytes = new byte[modelInputStream.available()];
            modelInputStream.read(modelBytes);
        } catch (IOException e) {
            Log.e(TAG, "Could not read model from Assets", e);
        }
        return modelBytes;
    }

    private List<String> importTextAssetList(Context context, String name){
        try(InputStream inputStream = context.getAssets().open(name)) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
            StringBuilder result = new StringBuilder();
            String buffer;
            List<String> ret = new ArrayList<>();
            while ((buffer = reader.readLine()) != null) {
                ret.add(buffer);
            }
            return ret;
        } catch (IOException e) {
            Log.e(TAG, "Could not read \"" + name + "\"  from Assets", e);
        }
        return null;
    }
    private String importTextAsset(Context context, String name){
        try(InputStream inputStream = context.getAssets().open(name)) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
            StringBuilder result = new StringBuilder();
            String buffer;
            while ((buffer = reader.readLine()) != null) {
                result.append(buffer);
            }
            return result.toString();
        } catch (IOException e) {
            Log.e(TAG, "Could not read \"" + name + "\"  from Assets", e);
        }
        return null;
    }

    private void loadDogsCatsModel(){
        File outputDir = ((Activity) MapView.getMapView().getContext()).getCacheDir();
        File outputFile = null;
        try {
            outputFile = File.createTempFile("dogs_cats_model", ".torchscript", outputDir);
        } catch (IOException e) {
            Log.e(TAG, "IOException writing to temp file", e);
        }

        /// Import Model from Assets Folder
        byte[] modelBytes = importModelFromAssets(pluginContext, "dogs_cats_model.torchscript");

        try(FileOutputStream fileOutputStream = new FileOutputStream(outputFile)){
            fileOutputStream.write(modelBytes);
        } catch (FileNotFoundException e) {
            Log.e(TAG, "FileNotFoundException writing to temp file", e);
        } catch (IOException e) {
            Log.e(TAG, "IOException writing to temp file", e);
        }

        // Create a TAK ML Model Wrapper
        TakmlModel takmlModel = new TakmlModel.TakmlModelBuilder("Dogs and Cats Pytorch",
                outputFile, ".torchscript", ModelTypeConstants.IMAGE_CLASSIFICATION)
                .setLabels(Arrays.asList("cat", "dog"))
                .build();
        takml.addTakmlModel(takmlModel);
    }

    private void loadDogsCatsTfliteModel(){
        File outputDir = ((Activity) MapView.getMapView().getContext()).getCacheDir();
        File outputFile = null;
        try {
            outputFile = File.createTempFile("dogs_cats_model", ".tflite", outputDir);
        } catch (IOException e) {
            Log.e(TAG, "IOException writing to temp file", e);
        }

        /// Import Model from Assets Folder
        byte[] modelBytes = importModelFromAssets(pluginContext, "dogs_and_cats_model.tflite");

        try(FileOutputStream fileOutputStream = new FileOutputStream(outputFile)){
            fileOutputStream.write(modelBytes);
        } catch (FileNotFoundException e) {
            Log.e(TAG, "FileNotFoundException writing to temp file", e);
        } catch (IOException e) {
            Log.e(TAG, "IOException writing to temp file", e);
        }

        // Create a TAK ML Model Wrapper
        TakmlModel takmlModel = new TakmlModel.TakmlModelBuilder("Dogs and Cats Tflite",
                outputFile, ".tflite", ModelTypeConstants.IMAGE_CLASSIFICATION)
                .setLabels(Arrays.asList("cat", "dog"))
                .build();
        takml.addTakmlModel(takmlModel);
    }

    private void loadVisdroneModel(){
        List<String> labels = importTextAssetList(pluginContext, "visdrone_labels.txt");
        String processingConfig = importTextAsset(pluginContext, "visdrone_input_processing_config.txt");
        PytorchObjectDetectionParams params = new Gson().fromJson(processingConfig, PytorchObjectDetectionParams.class);

        File outputDir = ((Activity) MapView.getMapView().getContext()).getCacheDir();
        File outputFile = null;
        try {
            outputFile = File.createTempFile("visdrone", ".torchscript", outputDir);
        } catch (IOException e) {
            Log.e(TAG, "IOException writing to temp file", e);
        }

        /// Import Model from Assets Folder
        byte[] modelBytes = importModelFromAssets(pluginContext, "visdrone.torchscript");

        try(FileOutputStream fileOutputStream = new FileOutputStream(outputFile)){
            fileOutputStream.write(modelBytes);
        } catch (FileNotFoundException e) {
            Log.e(TAG, "FileNotFoundException writing to temp file", e);
        } catch (IOException e) {
            Log.e(TAG, "IOException writing to temp file", e);
        }

        // Create a TAK ML Model Wrapper
        TakmlModel takmlModel = new TakmlModel.TakmlModelBuilder("Visdrone Pytorch",
                outputFile, ".torchscript", ModelTypeConstants.OBJECT_DETECTION)
                .setLabels(labels)
                .setProcessingParams(params)
                .build();
        takml.addTakmlModel(takmlModel);
    }

    /**************************** CONSTRUCTOR *****************************/

    public GenericImageRecognitionDropDownReceiver(final MapView mapView, final Context context) {
        super(mapView);
        this.pluginContext = context;
        LayoutInflater inflater = (LayoutInflater) context
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        templateView = inflater.inflate(R.layout.main_layout, null);
        pendingBar = templateView.findViewById(R.id.pendingBar);
        sendButton = templateView.findViewById(R.id.getPredictionButton);
        takml = new Takml(pluginContext);

        loadDogsCatsModel();
        loadDogsCatsTfliteModel();
        loadVisdroneModel();

        takml.addInitializationListener(new TakmlInitializationListener() {
            @Override
            public void finishedInitializing() {
                try {
                    List<TakmlModel> takmlModels = takml.getModels();
                    if (takmlModels.size()== 0){
                        Handler handler = new Handler(Looper.getMainLooper());
                        handler.postDelayed(() -> {
                            Toast.makeText(pluginContext, "No TAK ML models are found, please import then restart ATAK.",
                                    Toast.LENGTH_LONG).show();
                        }, 1000);
                    }else {
                        takmlExecutor = takml.createExecutor(takmlModels.iterator().next());
                    }
                } catch (TakmlInitializationException e) {
                    Log.e(TAG, "Could not create TAK ML Executor", e);
                }

                new Handler(Looper.getMainLooper()).post(() -> pendingBar.setVisibility(View.GONE));

                settingsButton = (Button) templateView.findViewById(R.id.settingsButton);
                settingsButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        Intent callbackIntent = new Intent();
                        callbackIntent.setAction(SHOW_PLUGIN);
                        if(takmlExecutor != null) {
                            takmlExecutor.showConfigUI(callbackIntent);
                        }else{
                            takml.showConfigUI(callbackIntent);
                        }
                    }
                });

                sendButton.setOnClickListener(new View.OnClickListener(){
                    @Override
                    public void onClick(View view) {
                        runPrediction();
                    }
                });

            }
        });
    }

    /**************************** PUBLIC METHODS *****************************/

    public void disposeImpl() {
    }

    /**************************** INHERITED METHODS *****************************/

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "showing plugin drop down");
        if (intent.getAction().equals(SHOW_PLUGIN)) {
            showDropDown(templateView, HALF_WIDTH, FULL_HEIGHT, FULL_WIDTH,
                    HALF_HEIGHT, false);

            imageView = templateView.findViewById(R.id.imageView);
            takeNewImageButton = templateView.findViewById(R.id.takeNewImageButton);
            loadNewImageButton = templateView.findViewById(R.id.loadNewImageButton);
            predictionResultsDisplay = templateView.findViewById(R.id.predictionResultDisplay);
            confidenceResultsDisplay = templateView.findViewById(R.id.confidenceResultDisplay);
            header = templateView.findViewById(R.id.header);

            if(takmlExecutor != null && takmlExecutor.getSelectedModel() != null) {
                header.setText("Model: " + takmlExecutor.getSelectedModel().getName());
            }else{
                header.setText("Model: ");
            }

            takeNewImageButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Log.d(TAG, "onClick: Camera button clicked");
                    cdl.register(getMapView().getContext(), cdr);
                    startCamera();
                }
            });

            loadNewImageButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    loadFileListener.register(getMapView().getContext(), loadFileReceiver);
                    startLoadActivity();
                }
            });
        }
    }

    public void startCamera() {
        Intent intent = new Intent();
        intent.setClassName(pluginContext.getPackageName(),
                CameraActivity.class.getName());
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        MapView.getMapView().getContext().startActivity(intent);
    }

    public void startLoadActivity() {
        Log.d(TAG, "Starting load activity.");
        Intent intent = new Intent();
        intent.setClassName(pluginContext.getPackageName(),
                LoadFileActivity.class.getName());
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        pluginContext.startActivity(intent);

    }

    @Override
    public void onDropDownSelectionRemoved() {
    }

    @Override
    public void onDropDownVisible(boolean v) {

    }
    @Override
    public void onDropDownSizeChanged(double width, double height) {
    }

    @Override
    public void onDropDownClose() {
    }

    private void disableUIInternal() {
        sendButton.setEnabled(false);
        takeNewImageButton.setEnabled(false);
        loadNewImageButton.setEnabled(false);
    }

    private void enableUIInternal() {
        sendButton.setEnabled(true);
        takeNewImageButton.setEnabled(true);
        loadNewImageButton.setEnabled(true);
    }
}
