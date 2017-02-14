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
import com.bringcommunications.etherpay.R;

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
        //The internal implementation of the support library just checks if the Toolbar has a title (not null) at the moment the SupportActionBar is
        //set up. If there is, then this title will be used instead of the window title. You can then set a dummy title while you load the real title.
        toolbar.setTitle("");
        toolbar.setBackgroundResource(R.color.color_toolbar);
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
        int subtitle_R = target_activity.equals("SendActivity") ? R.string.scan_subtitle_send : R.string.scan_subtitle_import;
        String subtitle = getResources().getString(subtitle_R);
        String app_name = getResources().getString(R.string.app_name);
        toolbar.setTitle(app_name);
        toolbar.setSubtitle(subtitle);
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
        System.out.println("ScanActivity: in onPause");
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
        try {
            mCamera.autoFocus(autoFocusCB);
        } catch (Exception e) {
            System.out.println("autofocus failed: " + e.toString());
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        switch (requestCode) {
            case REQUEST_CODE_ASK_PERMISSIONS:
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED)
                    do_scan_guts();
                else {
                    String msg = getResources().getString(R.string.cant_scan_cuz_no_access_msg);
                    Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
                }
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
		            //many QR codes simple contain the address, (eg 0x....); but i've seen this format also:
    		        //ethereum:<address>[?value=<value>][?gas=<suggestedGas>]
	    	        String to_addr = scanned_data;
                    if (scanned_data.contains(":0x")) {
                        int addr_idx = scanned_data.indexOf(':') + 1;
			            int end_idx = scanned_data.indexOf('?', addr_idx);
			            to_addr = (end_idx < 0) ? scanned_data.substring(addr_idx) : scanned_data.substring(addr_idx, end_idx);
                    }
		            String size_str = "0";
    		        if (scanned_data.contains("value=")) {
		                int size_idx = scanned_data.indexOf("value=") + "value=".length() + 1;
		                int end_idx = scanned_data.indexOf('?', size_idx);
		                size_str = (end_idx < 0) ? scanned_data.substring(size_idx) : scanned_data.substring(size_idx, end_idx);
		            }
                    intent.putExtra("TO_ADDR", to_addr);
                    intent.putExtra("SIZE", size_str);
                    intent.putExtra("DATA", "");
                    intent.putExtra("AUTO_PAY", "");
                    //finish scanactivity so back key doesn't bring us back here
                    System.out.println("ScanACtivity::onPreviewFrame -- starting SendActivity");
                    startActivity(intent);
                    this.finish();
                    break;
                }
                case "MainActivity": {
		            String app_uri = getResources().getString(R.string.app_uri);
                    SharedPreferences preferences = getSharedPreferences(app_uri, MODE_PRIVATE);
                    SharedPreferences.Editor preferences_editor = preferences.edit();
                    preferences_editor.putString("key", scanned_data);
                    preferences_editor.commit();
                    NavUtils.navigateUpFromSameTask(this);
                    this.finish();
                    break;
                }
                default: {
                    System.out.println("TARGET_ACTIVITY not set in ScanActivity");
                    break;
                }
            }
        }
    }

}
