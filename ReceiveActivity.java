package com.bringcommunications.etherpay;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.provider.MediaStore;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import net.glxn.qrgen.android.QRCode;

import java.io.ByteArrayOutputStream;

public class ReceiveActivity extends AppCompatActivity {

    private SharedPreferences preferences;
    TextView instructions_view;
    private String acct_addr = "";
    private Bitmap qr_bitmap;
    private static final int REQUEST_CODE_ASK_PERMISSIONS = 123;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_receive);
        instructions_view = (TextView) findViewById(R.id.instructions);
        String instructions = getResources().getString(R.string.receive_qr_prompt);
        instructions_view.setText(instructions);
        preferences = getSharedPreferences("etherpay.bringcommunications.com", MODE_PRIVATE);
        acct_addr = preferences.getString("acct_addr", acct_addr);
        ImageView qr_code_view = (ImageView) findViewById(R.id.qr_code);
        qr_bitmap = QRCode.from(acct_addr).bitmap();
        qr_code_view.setImageBitmap(qr_bitmap);
    }

    public void do_share(View view) {
        do_share_wrapper();
    }

    private void do_share_guts() {
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("image/*");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        qr_bitmap.compress(Bitmap.CompressFormat.JPEG, 100, bytes);

        String path = MediaStore.Images.Media.insertImage(getContentResolver(), qr_bitmap, "QR Code", null);
        Uri imageUri = Uri.parse(path);
        share.putExtra(Intent.EXTRA_STREAM, imageUri);
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
                else
                    Toast.makeText(this, "We cannot share unless you provide access to external storeage", Toast.LENGTH_SHORT).show();
                break;
            default:
                super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }


}

