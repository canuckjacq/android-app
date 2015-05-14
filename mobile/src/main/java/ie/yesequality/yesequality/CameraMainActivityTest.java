package ie.yesequality.yesequality;

import android.app.Activity;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.graphics.drawable.BitmapDrawable;
import android.hardware.Camera;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.DragEvent;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import butterknife.ButterKnife;
import butterknife.InjectView;
import butterknife.OnClick;
import ie.yesequality.yesequality.utils.BitmapUtils;
import ie.yesequality.yesequality.views.CameraOverlayView;

public class CameraMainActivityTest extends AppCompatActivity implements TextureView.SurfaceTextureListener,
        Camera.PictureCallback {
    public static final String TAG = "CameraMainActivity";
    private static final int PICTURE_QUALITY = 100;
    @InjectView(R.id.tbActionBar)
    protected Toolbar tbActionBar;
    @InjectView(R.id.rlSurfaceLayout)
    protected RelativeLayout rlSurfaceLayout;
    @InjectView(R.id.ivWaterMarkPic)
    protected ImageView ivWaterMarkPic;
    @InjectView(R.id.selfieButton)
    protected ImageView selfieButton;
    @InjectView(R.id.camera_overlay)
    protected CameraOverlayView cameraOverlayView;

    TextureView previewView;
    private Camera mCamera;

    private Camera.Size optimalSize;
    private int mCameraId;


    private int[] mVoteBadges = new int[]{R.drawable.ic_wm_vote_for_me,
            R.drawable.ic_wm_vote_for_me_color,
            R.drawable.ic_wm_yes_im_voting,
            R.drawable.ic_wm_yes_im_voting_color,
            R.drawable.ic_wm_we_voting,
            R.drawable.ic_wm_we_voting_color,
            R.drawable.ic_wm_ta,
            R.drawable.ic_wm_ta_color,
            R.drawable.ic_wm_yes,
            R.drawable.ic_wm_yes_color
    };

    private int mSelectedBadge = 0;

    public static String getPhotoDirectory(Context context) {
        return context.getExternalFilesDir(null).getPath();
    }

    /**
     * Determine the current display orientation and rotate the mCamera preview
     * accordingly.
     */
    public static int setCameraDisplayOrientation(Activity activity,
                                                  int cameraId, android.hardware.Camera camera) {
        android.hardware.Camera.CameraInfo info =
                new android.hardware.Camera.CameraInfo();
        android.hardware.Camera.getCameraInfo(cameraId, info);
        int rotation = activity.getWindowManager().getDefaultDisplay()
                .getRotation();
        int degrees = 0;
        switch (rotation) {
            case Surface.ROTATION_0:
                degrees = 0;
                break;
            case Surface.ROTATION_90:
                degrees = 90;
                break;
            case Surface.ROTATION_180:
                degrees = 180;
                break;
            case Surface.ROTATION_270:
                degrees = 270;
                break;
        }

        int result;
        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            result = (info.orientation + degrees) % 360;
            result = (360 - result) % 360;  // compensate the mirror
        } else {  // back-facing
            result = (info.orientation - degrees + 360) % 360;
        }

        return result;

    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // If the Android version is lower than Jellybean, use this call to hide
        // the status bar.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
            getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                    WindowManager.LayoutParams.FLAG_FULLSCREEN);
        } else {
            View decorView = getWindow().getDecorView();
            // Hide the status bar.
            int uiOptions = View.SYSTEM_UI_FLAG_FULLSCREEN;
            decorView.setSystemUiVisibility(uiOptions);
        }
        setContentView(R.layout.surface_camera_layout_test);
        ButterKnife.inject(this);

        tbActionBar.setTitle(R.string.app_name);
        tbActionBar.setOnMenuItemClickListener(new Toolbar.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                switch (item.getItemId()) {
                    case R.id.action_info:
                        Intent infoIntent = new Intent(CameraMainActivityTest.this, MainActivity
                                .class);
                        startActivity(infoIntent);
                        return true;
                    case R.id.action_reminders:
                        Intent reminderIntent = new Intent(CameraMainActivityTest.this,
                                NotificationActivity.class);
                        startActivity(reminderIntent);
                        return true;

                    default:
                        return false;

                }
            }
        });

        tbActionBar.inflateMenu(R.menu.menu_camera_main);

        final BadgeDragListener badgeDragListener = new BadgeDragListener();
        ivWaterMarkPic.setOnDragListener(badgeDragListener);


        ivWaterMarkPic.setOnTouchListener(new View.OnTouchListener() {
            float downX = 0, downY = 0;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        downX = event.getX();
                        downY = event.getY();
                        break;

                    case MotionEvent.ACTION_MOVE:
                        // This filters out smaller motions to try and stop the badge from
                        // shifting around while the user is trying to click on it.
                        if (Math.abs(downX - event.getX()) + Math.abs(downY - event.getY()) > 16
                                * getResources().getDisplayMetrics().density) {
                            ClipData data = ClipData.newPlainText("", "");
                            View.DragShadowBuilder shadowBuilder = new View.DragShadowBuilder(v);
                            v.startDrag(data, shadowBuilder, v, 0);
                            v.setVisibility(View.INVISIBLE);
                            return true;
                        }

                    default:
                        return false;
                }
                return false;
            }
        });


        ivWaterMarkPic.setImageResource(mVoteBadges[mSelectedBadge]);
        ivWaterMarkPic.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE).edit().putBoolean
                        (Constants.WATERMARK_CLICKED, true).apply();
                if (mSelectedBadge >= mVoteBadges.length - 1) {
                    mSelectedBadge = 0;
                } else {
                    mSelectedBadge++;
                }

                ivWaterMarkPic.setImageResource(mVoteBadges[mSelectedBadge]);
                ivWaterMarkPic.setVisibility(View.VISIBLE);
                ivWaterMarkPic.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
                    @Override
                    public void onLayoutChange(View v, int left, int top, int right, int bottom,
                                               int oldLeft, int oldTop, int oldRight, int
                                                       oldBottom) {
                        //You can't run this function until the ImageView has finished laying out
                        // with the new image
                        moveWaterMarkWithinBounds();
                        ivWaterMarkPic.removeOnLayoutChangeListener(this);
                    }
                });

            }
        });


        rlSurfaceLayout.setOnDragListener(new BadgeDragListener());
        cameraOverlayView.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int left, int top, int right, int bottom, int
                    oldLeft, int oldTop, int oldRight, int oldBottom) {
                Rect overlayInnerRect = cameraOverlayView.getInnerRect();
                rlSurfaceLayout.setLayoutParams(new FrameLayout.LayoutParams(overlayInnerRect
                        .width(), overlayInnerRect.height(), Gravity.CENTER));

                cameraOverlayView.removeOnLayoutChangeListener(this);
            }
        });

        if (!getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE).getBoolean(Constants
                .WATERMARK_CLICKED, false)) {
            showCustomToast("Tap the badge!");
        }

        previewView = (TextureView) findViewById(R.id.camera_fragment);
        previewView.setSurfaceTextureListener(this);
    }


    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        mCamera = null;

        if (Camera.getNumberOfCameras() == 0) {
            Toast.makeText(this, getString(R.string.error_unable_to_connect), Toast
                    .LENGTH_LONG).show();
            return;
        }

        mCameraId = 0;
        //First try to open a front facing camera
        try {
            Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
            for (int i = 0; i < Camera.getNumberOfCameras(); i++) {
                Camera.getCameraInfo(i, cameraInfo);

                if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {

                    mCamera = Camera.open(i);

                    mCameraId = i;
                    break;
                }
            }
        } catch (RuntimeException e) {
            mCamera = null;
        }

        //There were no front facing cameras, try to find a rear-facing one
        if (mCamera == null) {
            try {
                mCamera = Camera.open();
            } catch (RuntimeException e) {
                mCamera = null;
            }
        }

        //Ah well, who really wants a camera anyway?
        if (mCamera == null) {
            Toast.makeText(this, getString(R.string.error_unable_to_connect), Toast
                    .LENGTH_LONG).show();
            return;
        }

        mCamera.setDisplayOrientation(setCameraDisplayOrientation(this, mCameraId,
                mCamera));

        // stop preview before making changes
        try {
            mCamera.stopPreview();
        } catch (Exception e) {
            // ignore: tried to stop a non-existent preview
        }

        // set preview size and make any resize, rotate or
        // reformatting changes here
        Camera.Parameters params = mCamera.getParameters();
        params.set("orientation", "portrait");
        optimalSize = getOptimalPreviewSize(params.getSupportedPreviewSizes(),
                width, height);
        params.setPreviewSize(optimalSize.width, optimalSize.height);

        int smallSide = optimalSize.height < optimalSize.width ? optimalSize.height : optimalSize.width;
        int largeSide = optimalSize.height > optimalSize.width ? optimalSize.height : optimalSize.width;

        float scale = (float) rlSurfaceLayout.getWidth() / smallSide;
        previewView.setLayoutParams(new FrameLayout.LayoutParams(rlSurfaceLayout.getWidth(), (int) (scale * largeSide), Gravity.CENTER));

        Camera.Size pictureSize = getOptimalPreviewSize(params.getSupportedPictureSizes(), width, height);
        params.setPictureSize(pictureSize.width, pictureSize.height);
        mCamera.setParameters(params);
        // start preview with new settings
        try {
            mCamera.setPreviewTexture(surface);
            mCamera.startPreview();

        } catch (Exception e) {
            Log.d(TAG, "Error starting mCamera preview: " + e.getMessage());
        }
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
        // Ignored, Camera does all the work for us
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        if (mCamera != null) {
            mCamera.stopPreview();
            mCamera.release();
        }
        return true;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {
        // Invoked every time there's a new Camera preview frame
    }


    private Camera.Size getOptimalPreviewSize(List<Camera.Size> sizes, int w, int h) {
        final double ASPECT_TOLERANCE = 0.05;
        double targetRatio = (double) w / h;

        if (sizes == null) return null;

        Camera.Size optimalSize = null;

        double minDiff = Double.MAX_VALUE;

        // Find size
        for (Camera.Size size : sizes) {
            double ratio = (double) size.width / size.height;
            if (Math.abs(ratio - targetRatio) > ASPECT_TOLERANCE) continue;
            if (Math.abs(size.height - h) < minDiff) {
                optimalSize = size;
                minDiff = Math.abs(size.height - h);
            }
        }

        if (optimalSize == null) {
            minDiff = Double.MAX_VALUE;
            for (Camera.Size size : sizes) {
                if (Math.abs(size.height - h) < minDiff) {
                    optimalSize = size;
                    minDiff = Math.abs(size.height - h);
                }
            }
        }
        return optimalSize;
    }


    private void showCustomToast(String message) {
        Toast toast = new Toast(this);
        TextView textView = (TextView) LayoutInflater.from(this).inflate(R.layout.toast, null);
        textView.setText(message);
        textView.setTextColor(Color.WHITE);
        toast.setView(textView);
        toast.setGravity(Gravity.CENTER_VERTICAL | Gravity.LEFT, 0, 0);
        toast.setDuration(Toast.LENGTH_LONG);
        toast.show();
    }


    void moveWaterMarkWithinBounds() {
        float internalX = ivWaterMarkPic.getX();
        float internalY = ivWaterMarkPic.getY();

        if (internalX < ivWaterMarkPic.getWidth() / 2) {
            ivWaterMarkPic.setX(0);
        } else if (rlSurfaceLayout.getWidth() - internalX < ivWaterMarkPic.getWidth() / 2) {
            ivWaterMarkPic.setX(rlSurfaceLayout.getWidth() - ivWaterMarkPic.getWidth());
        } /*else {
            ivWaterMarkPic.setX(internalX - (ivWaterMarkPic.getWidth() / 2));
        }*/


        if (internalY < ivWaterMarkPic.getHeight() / 2) {
            ivWaterMarkPic.setY(0);
        } else if (rlSurfaceLayout.getHeight() - internalY < ivWaterMarkPic.getHeight() / 2) {
            ivWaterMarkPic.setY(rlSurfaceLayout.getHeight() - ivWaterMarkPic.getHeight());
        } /*else {
            ivWaterMarkPic.setY(internalY - (ivWaterMarkPic.getHeight() / 2));
        }*/
    }


    @Override
    protected void onResume() {
        super.onResume();
        selfieButton.setEnabled(true);

        if (previewView.getSurfaceTexture() != null && mCamera != null) {
            mCamera.startPreview();
        }
    }


    @OnClick(R.id.selfieButton)
    public void takePicture(View view) {
        view.setEnabled(false);

        if (mCamera != null) {
            mCamera.takePicture(null, null, this);
        } else {
            Toast.makeText(this, R.string.error_taking_picture, Toast.LENGTH_LONG).show();
        }
    }


    /**
     * A picture has been taken.
     */
    public void saveAndSharePicture(Bitmap bitmap) {
        File mediaStorageDir = new File(
                Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_PICTURES
                ),
                getString(R.string.app_name)
        );

        if (!mediaStorageDir.exists()) {
            if (!mediaStorageDir.mkdirs()) {
                showSavingPictureErrorToast();
                return;
            }
        }

        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format
                (new Date());
        File mediaFile = new File(
                mediaStorageDir.getPath() + File.separator + "yesequal_" + timeStamp + ".jpg"
        );

        try {
            FileOutputStream stream = new FileOutputStream(mediaFile);
            bitmap.compress(Bitmap.CompressFormat.JPEG, PICTURE_QUALITY, stream);
        } catch (IOException exception) {
            showSavingPictureErrorToast();

            Log.w(TAG, "IOException during saving bitmap", exception);
            return;
        }

        MediaScannerConnection.scanFile(
                this,
                new String[]{mediaFile.toString()},
                new String[]{"image/jpeg"},
                null
        );


        Intent intent = new Intent(this, PhotoActivity.class);
        intent.setData(Uri.fromFile(mediaFile));

        intent.putExtra(PhotoActivity.ABSOLUTE_Y, (int) rlSurfaceLayout.getY());
        startActivity(intent);
    }

    private void showSavingPictureErrorToast() {
        Toast.makeText(this, "Error saving picture", Toast.LENGTH_SHORT).show();
    }

    /**
     * Take a picture and notify the listener once the picture is taken.
     */
    public void takePicture() {
        if (mCamera != null) {
            mCamera.takePicture(null, null, this);
        } else {
            Toast.makeText(this, R.string.error_taking_picture, Toast.LENGTH_LONG).show();
        }
    }


    /**
     * A picture has been taken.
     */
    @Override
    public void onPictureTaken(byte[] data, Camera camera) {
        Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length);

        Matrix matrix = new Matrix();
        matrix.preScale(1, -1);

        matrix.postRotate((180 + setCameraDisplayOrientation(this, mCameraId,
                mCamera)) % 360);

        bitmap = Bitmap.createBitmap(
                bitmap,
                Math.abs(optimalSize.width - bitmap.getWidth()) / 2,
                Math.abs(optimalSize.height - bitmap.getHeight()) / 2,
                optimalSize.width,
                optimalSize.height,
                matrix,
                false
        );


        Bitmap waterMark = ((BitmapDrawable) ivWaterMarkPic.getDrawable()).getBitmap();

        bitmap = BitmapUtils.cropBitmapToSquare(bitmap);

        bitmap = BitmapUtils.overlayBitmap(bitmap, waterMark, ivWaterMarkPic.getX(), ivWaterMarkPic.getY(),
                rlSurfaceLayout.getWidth(), rlSurfaceLayout.getHeight());


        saveAndSharePicture(bitmap);
    }


    private final class BadgeDragListener implements View.OnDragListener {

        float internalX = 0, internalY = 0;

        @Override
        public boolean onDrag(View v, DragEvent event) {
            int action = event.getAction();
            switch (action) {
                case DragEvent.ACTION_DRAG_LOCATION:
                    internalX = event.getX();
                    internalY = event.getY();

                    break;

                case DragEvent.ACTION_DROP:
                    View view = (View) event.getLocalState();
                    view.setX(event.getX() - (view.getWidth() / 2));
                    view.setY(event.getY() - (view.getHeight() / 2));
                    view.setVisibility(View.VISIBLE);
                    break;

                case DragEvent.ACTION_DRAG_ENDED:
                    View eventView = ((View) event.getLocalState());

                    if (internalX < eventView.getWidth() / 2) {
                        eventView.setX(0);
                    } else if (rlSurfaceLayout.getWidth() - internalX < eventView.getWidth() / 2) {
                        eventView.setX(rlSurfaceLayout.getWidth() - eventView.getWidth());
                    } else {
                        eventView.setX(internalX - (eventView.getWidth() / 2));
                    }


                    if (internalY < eventView.getHeight() / 2) {
                        eventView.setY(0);
                    } else if (rlSurfaceLayout.getHeight() - internalY < eventView.getHeight() /
                            2) {
                        eventView.setY(rlSurfaceLayout.getHeight() - eventView.getHeight());
                    } else {
                        eventView.setY(internalY - (eventView.getHeight() / 2));
                    }

                    eventView.setVisibility(View.VISIBLE);
                    break;
                default:
                    break;
            }
            return true;
        }
    }
}