package com.bringcommunications.etherpay;


//import net.sourceforge.zbar.android.CameraTest.CameraPreview;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.NavUtils;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;

import android.view.Menu;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.Button;

import android.hardware.Camera;
import android.hardware.Camera.PreviewCallback;
import android.hardware.Camera.AutoFocusCallback;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.Size;

import android.widget.TextView;
import android.graphics.ImageFormat;
import android.widget.Toast;

/* Import ZBar Class files */
import net.sourceforge.zbar.ImageScanner;
import net.sourceforge.zbar.Image;
import net.sourceforge.zbar.Symbol;
import net.sourceforge.zbar.SymbolSet;
import net.sourceforge.zbar.Config;

public class ScanActivity
        //extends Activity
        extends AppCompatActivity
        implements PreviewCallback
{
    private FrameLayout overlay_frame_layout;
    private String target_activity;
    private Camera mCamera;
    private CameraPreview mPreview;
    private Handler autoFocusHandler;
    private ScanActivity context;
    TextView instructions_view;
    private static final int REQUEST_CODE_ASK_PERMISSIONS = 123;

    //Button scanButton;

    ImageScanner scanner;

    private boolean barcodeScanned = false;
    private boolean previewing = true;


    static {
        System.loadLibrary("iconv");
    }

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        context = this;
        overlay_frame_layout = new FrameLayout(getApplicationContext());
        setContentView(overlay_frame_layout);
        View activity_scan_view = getLayoutInflater().inflate(R.layout.activity_scan, overlay_frame_layout, false);
        setContentView(activity_scan_view);
        Toolbar toolbar = (Toolbar)findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        //
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        target_activity = getIntent().getStringExtra("TARGET_ACTIVITY");
        String scan_prompt = getIntent().getStringExtra("SCAN_PROMPT");
        autoFocusHandler = new Handler();
        //Instance barcode scanner
        scanner = new ImageScanner();
        scanner.setConfig(0, Config.X_DENSITY, 3);
        scanner.setConfig(0, Config.Y_DENSITY, 3);
        //
        instructions_view = (TextView)findViewById(R.id.instructions);
        instructions_view.setText(scan_prompt);
        barcodeScanned = false;
    }

    public boolean onCreateOptionsMenu(Menu menu) {
        //no options menu
        return(false);
    }


    @Override protected void onStart() {
        super.onStart();
        do_scan_wrapper();
    }

    public void onPause() {
        super.onPause();
        System.out.println("in onPause");
        releaseCamera();
    }

    private void do_scan_wrapper() {
        int has_write_permission = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA);
        if (has_write_permission != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, REQUEST_CODE_ASK_PERMISSIONS);
            return;
        }
        do_scan_guts();
    }

    private void do_scan_guts() {
        mCamera = getCameraInstance();
        mPreview = new CameraPreview(this, mCamera, this, autoFocusCB);
        FrameLayout preview = (FrameLayout)findViewById(R.id.cameraPreview);
        preview.addView(mPreview);
        mCamera.setPreviewCallback(this);
        mCamera.startPreview();
        previewing = true;
        mCamera.autoFocus(autoFocusCB);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        switch (requestCode) {
            case REQUEST_CODE_ASK_PERMISSIONS:
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED)
                    do_scan_guts();
                else
                    Toast.makeText(this, "We cannot scan access the camera", Toast.LENGTH_SHORT).show();
                break;
            default:
                super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }


    /** A safe way to get an instance of the Camera object. */
    public static Camera getCameraInstance(){
        Camera c = null;
        try {
            c = Camera.open();
        } catch (Exception e){
            System.out.println(e.toString());
        }
        return c;
    }

    private void releaseCamera() {
        if (mCamera != null) {
            previewing = false;
            mCamera.setPreviewCallback(null);
            mCamera.stopPreview();
            mCamera.release();
            mCamera = null;
        }
    }

    // Mimic continuous auto-focusing
     // Mimic continuous auto-focusing
    AutoFocusCallback autoFocusCB = new AutoFocusCallback() {
            public void onAutoFocus(boolean success, Camera camera) {
                if (previewing && mCamera != null)
                    autoFocusHandler.postDelayed(doAutoFocus, 1000);
            }
    };


    private Runnable doAutoFocus = new Runnable() {
            public void run() {
                if (previewing && mCamera != null)
                    mCamera.autoFocus(autoFocusCB);
            }
        };

    //to fullfill contract as a PreviewCallback
    public void onPreviewFrame(byte[] data, Camera camera) {
        if (!previewing || camera == null || mCamera == null)
            return;
        Camera.Parameters parameters = camera.getParameters();
        Size size = parameters.getPreviewSize();

        Image barcode = new Image(size.width, size.height, "Y800");
        barcode.setData(data);

        int result = scanner.scanImage(barcode);
        if (result != 0) {
            previewing = false;
            mCamera.setPreviewCallback(null);
            mCamera.stopPreview();
            String scanned_data = "no data";
            SymbolSet syms = scanner.getResults();
            for (Symbol sym : syms) {
                //scanText.setText("barcode result " + sym.getData());
                scanned_data = sym.getData();
                barcodeScanned = true;
            }
            switch (target_activity){
                case "SendActivity": {
                    Intent intent = new Intent(this, SendActivity.class);
                    if (scanned_data.contains(":0x")) {
                        int colon_idx = scanned_data.indexOf(':');
                        scanned_data = scanned_data.substring(colon_idx + 1);
                    }
                    intent.putExtra("TO_ADDR", scanned_data);
                    intent.putExtra("SIZE", "0");
                    intent.putExtra("DATA", "");
                    intent.putExtra("AUTO_PAY", "");
                    //finish scanactivity so back key doesn't bring us back here
                    startActivity(intent);
                    this.finish();
                    break;
                }
                case "MainActivity": {
                    SharedPreferences preferences = getSharedPreferences("etherpay.bringcommunications.com", MODE_PRIVATE);
                    SharedPreferences.Editor preferences_editor = preferences.edit();
                    preferences_editor.putString("key", scanned_data);
                    preferences_editor.commit();
                    NavUtils.navigateUpFromSameTask(this);
                    this.finish();
                    break;
                }
                default: {
                    Toast.makeText(this, "TARGET_ACTIVITY not set in ScanActivity", Toast.LENGTH_SHORT).show();
                    break;
                }
            }
        }
    }

}
