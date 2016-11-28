package com.bringcommunications.etherpay;

import android.content.Context;
import android.os.AsyncTask;
import android.os.CountDownTimer;
import android.widget.Toast;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

/**
 * Created by dbrosen on 11/25/16.
 */

public class Util {
    static public void show_err(Context context, String msg, int seconds) {
        //toast LENGTH_LONG is 3.5 secs. (and LENGTH_SHORT is 2.5) we need to display this about 10 secs
        final Toast tag = Toast.makeText(context, msg, Toast.LENGTH_SHORT);
        tag.show();
        new CountDownTimer(1000 * seconds, 1000) {
            public void onTick(long millisUntilFinished) { tag.show(); }
            public void onFinish()                       { tag.show(); }
        }.start();
    }

}
