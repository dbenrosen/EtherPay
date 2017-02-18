/*

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.  IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
THE SOFTWARE.

*/

package com.bringcommunications.etherpay;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import net.glxn.qrgen.android.QRCode;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;

public class ReceiveActivity extends AppCompatActivity {

    private FrameLayout overlay_frame_layout;
    private SharedPreferences preferences;
    TextView instructions_view;
    private String acct_addr = "";
    private String private_key = "";
    private Bitmap qr_bitmap;
    private boolean show_private = false;
    private static final int REQUEST_CODE_ASK_PERMISSIONS = 123;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        overlay_frame_layout = new FrameLayout(getApplicationContext());
        setContentView(overlay_frame_layout);
        View activity_receive_view = getLayoutInflater().inflate(R.layout.activity_receive, overlay_frame_layout, false);
        setContentView(activity_receive_view);
        show_private = getIntent().getBooleanExtra("SHOW_PRIVATE", false);
        Toolbar toolbar = (Toolbar)findViewById(R.id.toolbar);
        int subtitle_R = show_private ? R.string.receive_subtitle_export : R.string.receive_subtitle_receive;
        String subtitle = getResources().getString(subtitle_R);
        String app_name = getResources().getString(R.string.app_name);
        toolbar.setTitle(app_name);
        toolbar.setSubtitle(subtitle);
        toolbar.setBackgroundResource(R.color.color_toolbar);
        setSupportActionBar(toolbar);
        instructions_view = (TextView) findViewById(R.id.instructions);
        String instructions = (show_private) ? getResources().getString(R.string.export_acct_prompt) : getResources().getString(R.string.receive_qr_prompt);
        instructions_view.setText(instructions);
    	String app_uri = getResources().getString(R.string.app_uri);
        ImageView qr_code_view = (ImageView) findViewById(R.id.qr_code);
        preferences = getSharedPreferences(app_uri, MODE_PRIVATE);
        acct_addr = preferences.getString("acct_addr", acct_addr);
        private_key = preferences.getString("key", private_key);
        if (show_private) {
            //once we show the private key, we can't know if the acct is used (has tx's) or not
            SharedPreferences.Editor preferences_editor = preferences.edit();
            preferences_editor.putBoolean("acct_has_no_txs", false);
            preferences_editor.apply();
            qr_bitmap = QRCode.from(private_key).bitmap();
        } else {
            qr_bitmap = QRCode.from(acct_addr).bitmap();
        }
        qr_code_view.setImageBitmap(qr_bitmap);
    }

    public boolean onCreateOptionsMenu(Menu menu) {
        //no options menu
        return(false);
    }


    public void do_share(View view) {
        do_share_wrapper();
    }


    private void do_share_guts() {
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("image/*");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        qr_bitmap.compress(Bitmap.CompressFormat.JPEG, 100, bytes);

        String path = null;
        try {
            path = MediaStore.Images.Media.insertImage(getContentResolver(), qr_bitmap, "QR Code", null);
            if (path == null) {
                //turns out there's an Android bug, where it happens when the user hasn't taken a photo on the device before (i.e. gallery is empty
                //and hasn't been initialized.). The workaround is to initialize the photo directory manually:
                File sdcard = Environment.getExternalStorageDirectory();
                if (sdcard != null) {
                    File mediaDir = new File(sdcard, "DCIM/Camera");
                    if (!mediaDir.exists())
                        mediaDir.mkdirs();
                }
                path = MediaStore.Images.Media.insertImage(getContentResolver(), qr_bitmap, "QR Code", null);
            }
        } catch (Exception e) { }
        if (path == null) {
            String msg = getResources().getString(R.string.receive_err_no_media_access_msg);
            Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
            return;
        }

        Uri imageUri = Uri.parse(path);
        share.putExtra(Intent.EXTRA_STREAM, imageUri);
        if (show_private)
            share.putExtra(Intent.EXTRA_TEXT, private_key);
        else
            share.putExtra(Intent.EXTRA_TEXT, acct_addr);
        try {
            startActivity(Intent.createChooser(share, "Select"));
        } catch (android.content.ActivityNotFoundException ex) {
           ex.printStackTrace();
        }
    }

    private void do_share_wrapper() {
        int has_write_permission = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE);
        if (has_write_permission != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_CODE_ASK_PERMISSIONS);
            return;
        }
        do_share_guts();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        switch (requestCode) {
            case REQUEST_CODE_ASK_PERMISSIONS:
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED)
                    do_share_guts();
                else {
                    String msg = getResources().getString(R.string.receive_err_no_media_access_permission);
                    Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
                }
                break;
            default:
                super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }


}

