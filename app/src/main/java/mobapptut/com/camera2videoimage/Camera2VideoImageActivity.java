package mobapptut.com.camera2videoimage;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import static mobapptut.com.camera2videoimage.R.id.imageView;

public class Camera2VideoImageActivity extends AppCompatActivity {

    private long start;
    private long end;
    private long t;
    private ImageView mImageView;
    private Bitmap mBitmap;
    private Bitmap mBitmapGray;
    public long exposureDuration =1000000000l / 30;
    /////////////


//    private  int FPS

    private static final String TAG = "Camera2VideoImageActivi";

    private static final int REQUEST_CAMERA_PERMISSION_RESULT = 0;
    private static final int REQUEST_WRITE_EXTERNAL_STORAGE_PERMISSION_RESULT = 1;
    private static final int STATE_PREVIEW = 0;
    private static final int STATE_WAIT_LOCK = 1;
    private int mCaptureState = STATE_PREVIEW;

    private CameraDevice mCameraDevice;
    private CameraDevice.StateCallback mCameraDeviceStateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(CameraDevice camera) {
            mCameraDevice = camera;
            startPreview();
        }

        @Override
        public void onDisconnected(CameraDevice camera) {
            camera.close();
            mCameraDevice = null;
        }

        @Override
        public void onError(CameraDevice camera, int error) {
            camera.close();
            mCameraDevice = null;
        }
    };
    private HandlerThread mBackgroundHandlerThread;
    private Handler mBackgroundHandler;
    private Handler handler;
    private String mCameraId;
    private Size mImageSize;
    private Mat mRgba;
    private Mat mGray;

    static {
        if (OpenCVLoader.initDebug()){
            Log.i(TAG,"OpenCV loaded successfully");
        }else{
            Log.i(TAG,"OpenCV not loaded");
        }
    }

    static {
        System.loadLibrary("MyOpencvLibs");
    }


    public static Mat imageToMat(Image image) {
        ByteBuffer buffer;
        int rowStride;
        int pixelStride;
        int width;
        int height;

        width = image.getWidth();
        height = image.getHeight();

        int offset = 0;

        Image.Plane[] planes = image.getPlanes();
        byte[] data = new byte[image.getWidth() * image.getHeight() * ImageFormat.getBitsPerPixel(ImageFormat.YUV_420_888) / 8];
        byte[] rowData = new byte[planes[0].getRowStride()];

        for (int i = 0; i < planes.length; i++) {
            buffer = planes[i].getBuffer();
            rowStride = planes[i].getRowStride();
            pixelStride = planes[i].getPixelStride();
            int w = (i == 0) ? width : width / 2;
            int h = (i == 0) ? height : height / 2;
            for (int row = 0; row < h; row++) {
                int bytesPerPixel = ImageFormat.getBitsPerPixel(ImageFormat.YUV_420_888) / 8;
                if (pixelStride == bytesPerPixel) {
                    int length = w * bytesPerPixel;
                    buffer.get(data, offset, length);

                    if (h - row != 1) {
                        buffer.position(buffer.position() + rowStride - length);
                    }
                    offset += length;
                } else {


                    if (h - row == 1) {
                        buffer.get(rowData, 0, width - pixelStride + 1);
                    } else {
                        buffer.get(rowData, 0, rowStride);
                    }

                    for (int col = 0; col < w; col++) {
                        data[offset++] = rowData[col * pixelStride];
                    }
                }
            }
        }

        Mat mat = new Mat(height + height / 2, width, CvType.CV_8UC1);
        mat.put(0, 0, data);

        return mat;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if(mCameraDevice != null) {
            mCameraDevice.close();
            mCameraDevice = null;
        }

    }

    private ImageReader mImageReader;
    protected ImageSaver mImageSaver;
    private final ImageReader.OnImageAvailableListener mOnImageAvailableListener = new
            ImageReader.OnImageAvailableListener() {
                @Override
                public void onImageAvailable(ImageReader reader) {

                    mImageSaver = null;
                    mImageSaver = new ImageSaver(reader.acquireNextImage());
                    handler.post(mImageSaver);

                }
            };
    private class ImageSaver implements Runnable {

        private final Image mImage;

        public ImageSaver(Image image) {
            mImage = image;
        }

        @Override
        public void run() {
            try {

            start = System.currentTimeMillis();
//            ByteBuffer byteBuffer = mImage.getPlanes()[0].getBuffer();
//            byte[] bytes = new byte[byteBuffer.remaining()];
//            byteBuffer.get(bytes);

            Mat mYuvMat = imageToMat(mImage);
            Mat bgrMat = new Mat(mImage.getHeight(), mImage.getWidth(),CvType.CV_8UC4);


            mImage.close();

            Imgproc.cvtColor(mYuvMat, bgrMat, Imgproc.COLOR_YUV2BGR_I420);

//            mBitmap = BitmapFactory.decodeByteArray(data, 0, data.length);

            Mat rgbaMatOut = new Mat();
            Imgproc.cvtColor(bgrMat, rgbaMatOut, Imgproc.COLOR_BGR2RGBA, 0);
            final Bitmap bitmap = Bitmap.createBitmap(bgrMat.cols(), bgrMat.rows(), Bitmap.Config.ARGB_8888);
            Utils.matToBitmap(rgbaMatOut, bitmap);

            if ( exposureDuration == 1000000000l/16000) {

                Utils.bitmapToMat(bitmap, mRgba);
                NativeClass.lightDetection(mRgba.getNativeObjAddr());
//                NativeClass.lightDetection(rgbaMatOut.getNativeObjAddr());
                Utils.matToBitmap(mRgba, bitmap);
//                NativeClass.lightDetection(rgbaMatOut.getNativeObjAddr());
            }


//            Utils.matToBitmap(mGray, bitmap);
//            int chieudaitext = testNativeClass.getJniStringBytes().length();
            tvNative.setText(""+testNativeClass.getJniStringBytes());
            System.out.print("text data: "+testNativeClass.getJniStringBytes()+ "\n");

            mImageView.setImageBitmap(bitmap);
//            System.out.println("rong + dai:" + bitmap.getWidth() + bitmap.getHeight() );
            end = System.currentTimeMillis();
            mImageView.setRotation(90);
            t = end - start;
            System.out.println("Total time:" + t + "milisecond" );
        }
        catch (Exception e){
            e.printStackTrace();
        }
        }
    }

    private int mTotalRotation;
    private CameraCaptureSession mPreviewCaptureSession;

    private CaptureRequest.Builder mCaptureRequestBuilder;

    private ImageButton mRecordImageButton;
    private ImageButton mStillImageButton;
    private boolean mIsRecording = false;
    private boolean mIsTimelapse = false;

    private static SparseIntArray ORIENTATIONS = new SparseIntArray();
    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 0);
        ORIENTATIONS.append(Surface.ROTATION_90, 90);
        ORIENTATIONS.append(Surface.ROTATION_180, 180);
        ORIENTATIONS.append(Surface.ROTATION_270, 270);
    }

    private static class CompareSizeByArea implements Comparator<Size> {

        @Override
        public int compare(Size lhs, Size rhs) {
            return Long.signum( (long)(lhs.getWidth() * lhs.getHeight()) -
                    (long)(rhs.getWidth() * rhs.getHeight()));
        }
    }

    public TextView tvNative;
    public NativeClass testNativeClass = new NativeClass();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera2_video_image);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        tvNative = (TextView) findViewById(R.id.textView);

        mStillImageButton = (ImageButton) findViewById(R.id.cameraImageButton2);
        mStillImageButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
//                checkWriteStoragePermission();
//                lockFocus();
                double temp = 16000;
                if (mCaptureRequestBuilder == null) {
                    return;
                }
                long ae = (long)(1000000000l/16000);
                mCaptureRequestBuilder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, ae);
                exposureDuration = ae;
                updatePreview();

            }
        });

        mImageView = (ImageView) findViewById(imageView);

        setupCamera(600, 800);
        connectCamera();
//        mRecordImageButton = (ImageButton) findViewById(R.id.videoOnlineImageButton);
//        mRecordImageButton.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View v) {
//                if (mIsRecording || mIsTimelapse) {
////                    mChronometer.stop();
////                    mChronometer.stop();
////                    mChronometer.setVisibility(View.INVISIBLE);
//                    mIsRecording = false;
//                    mIsTimelapse = false;
//                    mRecordImageButton.setImageResource(R.mipmap.btn_video_online);
//                    mMediaRecorder.stop();
//                    mMediaRecorder.reset();
//
//                    Intent mediaStoreUpdateIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
//                    mediaStoreUpdateIntent.setData(Uri.fromFile(new File(mVideoFileName)));
//                    sendBroadcast(mediaStoreUpdateIntent);
//
//                    startPreview();
//                } else {
//                    mIsRecording = true;
//                    mRecordImageButton.setImageResource(R.mipmap.btn_video_busy);
//                    checkWriteStoragePermission();
//                }
//            }
//        });
//        mRecordImageButton.setOnLongClickListener(new View.OnLongClickListener() {
//            @Override
//            public boolean onLongClick(View v) {
//                mIsTimelapse =true;
//                mRecordImageButton.setImageResource(R.mipmap.btn_timelapse);
//                checkWriteStoragePermission();
//                return true;
//            }
//        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        startBackgroundThread();
        if (mImageView.isActivated()) {
            setupCamera(600, 800);
            connectCamera();
//        } else {
//            mTextureView.setSurfaceTextureListener(mSurfaceTextureListener);
//            startPreview();
//            setupCamera(960, 1280);
//            connectCamera();
        }
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(mImageSaver);
        closeCamera();
        stopBackgroundThread();
        super.onPause();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocas) {
        super.onWindowFocusChanged(hasFocas);
        View decorView = getWindow().getDecorView();
        if(hasFocas) {
            decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
        }
    }

    private void setupCamera(int width, int height) {
        CameraManager cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            for(String cameraId : cameraManager.getCameraIdList()){
                CameraCharacteristics cameraCharacteristics = cameraManager.getCameraCharacteristics(cameraId);
                if(cameraCharacteristics.get(CameraCharacteristics.LENS_FACING) ==
                        CameraCharacteristics.LENS_FACING_FRONT){
                    continue;
                }
                StreamConfigurationMap map = cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                int deviceOrientation = getWindowManager().getDefaultDisplay().getRotation();
                mTotalRotation = sensorToDeviceRotation(cameraCharacteristics, deviceOrientation);
                mTotalRotation = sensorToDeviceRotation(cameraCharacteristics, deviceOrientation);
                boolean swapRotation = mTotalRotation == 90 || mTotalRotation == 270;

                int rotatedWidth = width;
                int rotatedHeight = height;
                if(swapRotation) {
                    rotatedWidth = height;
                    rotatedHeight = width;
                }

                System.out.println("do rong hinh:" + width );
                System.out.println("do dai hinh:" + height );

//                mPreviewSize = chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class), rotatedWidth, rotatedHeight);
//                mVideoSize = chooseOptimalSize(map.getOutputSizes(MediaRecorder.class), rotatedWidth, rotatedHeight);
                mImageSize = chooseOptimalSize(map.getOutputSizes(ImageFormat.YUV_420_888), rotatedWidth, rotatedHeight);

                //////////
                mBitmap = Bitmap.createBitmap(mImageSize.getWidth(), mImageSize.getHeight(), Bitmap.Config.ARGB_8888);
                mBitmapGray = Bitmap.createBitmap(mImageSize.getWidth(), mImageSize.getHeight(), Bitmap.Config.ARGB_8888);
                mRgba = new Mat(mImageSize.getHeight(), mImageSize.getWidth(), CvType.CV_8UC3);
                mGray = new Mat(mImageSize.getHeight(), mImageSize.getWidth(), CvType.CV_8UC4);

                mImageReader = ImageReader.newInstance(mImageSize.getWidth(), mImageSize.getHeight(), ImageFormat.YUV_420_888, 2);
                mImageReader.setOnImageAvailableListener(mOnImageAvailableListener,handler);
                mCameraId = cameraId;
                return;
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void connectCamera() {
        CameraManager cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if(ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) ==
                        PackageManager.PERMISSION_GRANTED) {
                    cameraManager.openCamera(mCameraId, mCameraDeviceStateCallback, mBackgroundHandler);
                } else {
                    if(shouldShowRequestPermissionRationale(android.Manifest.permission.CAMERA)) {
                        Toast.makeText(this,
                                "Video app required access to camera", Toast.LENGTH_SHORT).show();
                    }
                    requestPermissions(new String[] {android.Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO
                    }, REQUEST_CAMERA_PERMISSION_RESULT);
                }

            } else {
                cameraManager.openCamera(mCameraId, mCameraDeviceStateCallback, mBackgroundHandler);
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    protected void setupRequest( CaptureRequest.Builder request)
    {
        request.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF);
        request.set(CaptureRequest.SENSOR_EXPOSURE_TIME, exposureDuration);
//                request.set(CaptureRequest.SENSOR_SENSITIVITY, SettingCamera.iso);
//        }
        request.set(CaptureRequest.SENSOR_FRAME_DURATION, 1000000000l /20);

    }
    private void startPreview() {

        try {
            mCaptureRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            setupRequest(mCaptureRequestBuilder);
//            mCaptureRequestBuilder.addTarget(previewSurface);
            mCaptureRequestBuilder.addTarget(mImageReader.getSurface());
            mCaptureRequestBuilder.set(CaptureRequest.JPEG_ORIENTATION, mTotalRotation);

            mCameraDevice.createCaptureSession(Arrays.asList( mImageReader.getSurface()),
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(CameraCaptureSession session) {
                            Log.d(TAG, "onConfigured: startPreview");
                            mPreviewCaptureSession = session;
                            updatePreview();
                        }

                        @Override
                        public void onConfigureFailed(CameraCaptureSession session) {
                            Log.d(TAG, "onConfigureFailed: startPreview");

                        }
                    }, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    public void updatePreview() {
        if (null == mCameraDevice) {
            return;
        }
        try {
            mPreviewCaptureSession.setRepeatingRequest(mCaptureRequestBuilder.build(), null, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * Update the camera preview. {@link #startPreview()} needs to be called in advance.
     */

    private void closeCamera() {
        if(mCameraDevice != null) {
            mCameraDevice.close();
            mCameraDevice = null;
        }
//        if(mMediaRecorder != null) {
//            mMediaRecorder.release();
//            mMediaRecorder = null;
//        }
        if (mImageReader != null) {
            mImageReader.close();
            mImageReader = null;

        }
    }

    private void startBackgroundThread() {
        mBackgroundHandlerThread = new HandlerThread("Camera2VideoImage");

        mBackgroundHandlerThread.start();
        mBackgroundHandler = new Handler(mBackgroundHandlerThread.getLooper());
        handler = new Handler(Looper.getMainLooper());
    }

    private void stopBackgroundThread() {
        mBackgroundHandlerThread.quitSafely();
        try {
            mBackgroundHandlerThread.join();
            mBackgroundHandlerThread.join();
            mBackgroundHandlerThread = null;
            mBackgroundHandler = null;
            handler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private static int sensorToDeviceRotation(CameraCharacteristics cameraCharacteristics, int deviceOrientation) {
        int sensorOrienatation = cameraCharacteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
        deviceOrientation = ORIENTATIONS.get(deviceOrientation);
        return (sensorOrienatation + deviceOrientation + 360) % 360;
    }

    private static Size chooseOptimalSize(Size[] choices, int width, int height) {
        List<Size> bigEnough = new ArrayList<Size>();
        for(Size option : choices) {
            if(option.getHeight() == option.getWidth() * height / width &&
                    option.getWidth() >= width && option.getHeight() >= height) {
                bigEnough.add(option);
            }
        }
        if(bigEnough.size() > 0) {
            return Collections.min(bigEnough, new CompareSizeByArea());
        } else {
            return choices[0];
        }
    }


}
