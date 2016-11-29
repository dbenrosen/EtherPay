package com.bringcommunications.etherpay;

import android.content.Context;
import android.os.AsyncTask;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

import static com.bringcommunications.etherpay.BuildConfig.DEBUG;

/**
 * Created by dbrosen on 11/25/16.
 */
public class HTTP_Query_Task extends AsyncTask<String, Void, Void> {

    // ------------------------------------------------------------------------------------------------------------------------------------------------------------
    // http_query_task -- function to perform an http query, and then execute a callback fcn
    // note that network access needs to be done in a separate thread in order to avoid android.os.NetworkOnMainThreadException
    // ------------------------------------------------------------------------------------------------------------------------------------------------------------
        private boolean exception_occurred;
        private String exception_msg = "";
        private String http_query_rsp = "";
        private String callback = "";
        private HTTP_Query_Client client;
        private Context context;

        HTTP_Query_Task(HTTP_Query_Client client, Context context)
        {
            this.client = client;
            this.context = context;
        }

        protected void onPreExecute() {
            exception_occurred = false;
        }

        protected void onProgressUpdate(Void... progress) {
        }

        protected Void doInBackground(String... parms) {
            String url = parms[0];
            callback = parms[1];
            send_http_query(url);
            return null;
        }

        protected void onPostExecute(Void result) {
            if (exception_occurred) {
                //toast LENGTH_LONG is 3.5 secs. (and LENGTH_SHORT is 2.5) we need to display this about 5 secs
                Util.show_err(context, "Please check your internet connection", 5);
                Util.show_err(context, exception_msg, 15);
            } else {
                client.handle_http_rsp(callback, http_query_rsp);
            }
        }

        public void send_http_query(String url_string) {
            if (BuildConfig.DEBUG)
                System.out.println("in send_http_query...");
            HttpURLConnection urlConnection = null;
            StringBuilder string_builder = new StringBuilder(2048);
            try {
                URL url = new URL(url_string);
                urlConnection = (HttpURLConnection) url.openConnection();
                InputStream in = new BufferedInputStream(urlConnection.getInputStream());
                String line;
                BufferedReader reader = new BufferedReader(new InputStreamReader(in));
                while ((line = reader.readLine()) != null)
                    string_builder.append(line);
            } catch (MalformedURLException e) {
                exception_occurred = true;
                exception_msg = "caught java.net.malformedurl";
                if (BuildConfig.DEBUG)
                    System.out.println("exception: " + e.toString());

            } catch (IOException e) {
                exception_occurred = true;
                exception_msg = "caught IOException: " + e.toString();
                if (BuildConfig.DEBUG)
                    System.out.println("exception: " + e.toString());
            } finally {
                urlConnection.disconnect();
            }
            http_query_rsp = string_builder.toString();
            if (BuildConfig.DEBUG)
                System.out.println("response: " + http_query_rsp);
        }
}
