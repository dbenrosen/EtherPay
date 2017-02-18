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

import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;


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

    private static final int DNS_TIMEOUT = 15000;
    private static final int HTTP_TIMEOUT = 10000;


    HTTP_Query_Task(HTTP_Query_Client client, Context context) {
        this.client = client;
        this.context = context;
    }

    protected void onPreExecute() { exception_occurred = false; }

    protected void onProgressUpdate(Void... progress) {
    }

    protected Void doInBackground(String... parms) {
        System.out.println("HTTP_Query_Task::doInBackground");
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
            client.handle_http_rsp(callback, "");
       } else {
            client.handle_http_rsp(callback, http_query_rsp);
       }
    }

    private static HashMap<String, DNS_Entry> dns_cache = new HashMap<String, DNS_Entry>();
    //class to lookup a dns entry in a separate thread
    public static class DNS_Resolver implements Runnable {
        private String domain;
        private InetAddress inet_addr = null;
        public DNS_Resolver(String domain) {
            this.domain = domain;
        }
        public void run() {
            try {
                InetAddress addr = null;
                long now = System.currentTimeMillis() / 1000;
                System.out.println("before lookup: sec = " + System.currentTimeMillis() / 1000);
                DNS_Entry dns_entry = dns_cache.get(domain);
                if (dns_entry == null)
                    dns_entry = new DNS_Entry(addr, now);
                else if (now - dns_entry.timestamp < 60 * 60 * 1000)
                    addr = dns_entry.addr;
                if (addr == null) {
                    System.out.println("before lookup: sec = " + System.currentTimeMillis() / 1000);
                    addr = InetAddress.getByName(domain);
                    System.out.println("after lookup: sec = " + System.currentTimeMillis() / 1000);
                    dns_entry.addr = addr;
                    dns_entry.timestamp = now;
                }
                dns_cache.put(domain, dns_entry);
                set(addr);
            } catch (UnknownHostException e) {
            }
        }
        public synchronized void set(InetAddress inet_addr) {
            this.inet_addr = inet_addr;
        }
        public synchronized InetAddress get() {
            return inet_addr;
        }
    }

    //Run a DNS lookup manually to be able to time it out.
    public static URL ResolveHostIP(String url_string, int timeout) throws MalformedURLException {
        URL url= new URL(url_string);
        //Resolve the host IP on a new thread
        DNS_Resolver dns_resolver = new DNS_Resolver(url.getHost());
        Thread thread = new Thread(dns_resolver);
        thread.start();
        //Join the thread for some time
        try {
            thread.join(timeout);
            System.out.println("after join: sec = " + System.currentTimeMillis() / 1000);
        } catch (InterruptedException e) {
            System.out.println("DNS lookup interrupted");
            return null;
        }
        //get the IP of the host
        InetAddress inet_addr = dns_resolver.get();
        if(inet_addr == null) {
            System.out.println("DNS timed out without resolution");
            System.out.println("sec = " + System.currentTimeMillis() / 1000);
            return null;
        }
        //rebuild the URL with the IP and return it
        //dont know how to do this for https yet.... so only do it for regular http... :(
        //anyhow, the main benefit still accrues, since we now know that we can look up the dbs entry...
        if (!url_string.startsWith("https"))
            url =  new URL(url.getProtocol(), inet_addr.getHostAddress(), url.getPort(), url.getFile());
        return url;
    }


    public void send_http_query(String url_string) {
        if (BuildConfig.DEBUG) {
            System.out.println("in send_http_query. url: " + url_string);
            System.out.println("sec = " + System.currentTimeMillis() / 1000);
        }
        //here some shenanigans to set a timeout on the dns lookup. otherwise the timeout on the urlconnection is pretty useless...
        URL url = null;
        try {
            url = ResolveHostIP(url_string, DNS_TIMEOUT);
        } catch (MalformedURLException e) {
            //Log.d("INFO",e.getMessage());
        }
        if (url == null) {
            //the DNS lookup timed out or failed.
            exception_occurred = true;
            exception_msg = "DNS Lookup Timeout or Exception";
            return;
        }
        if (BuildConfig.DEBUG)
            System.out.println("resolved url: " + url.toString() + "; sec = " + System.currentTimeMillis() / 1000);
        HttpURLConnection urlConnection = null;
        StringBuilder string_builder = new StringBuilder(2048);
        try {
            //System.out.println("before openconnection: sec = " + System.currentTimeMillis() / 1000);
            urlConnection = (HttpURLConnection) url.openConnection();
            //System.out.println("after openconnection: sec = " + System.currentTimeMillis() / 1000);
            urlConnection.setConnectTimeout(HTTP_TIMEOUT);
            InputStream in = new BufferedInputStream(urlConnection.getInputStream());
            String line;
            BufferedReader reader = new BufferedReader(new InputStreamReader(in));
            //System.out.println("before readline loop: sec = " + System.currentTimeMillis() / 1000);
            while ((line = reader.readLine()) != null)
                string_builder.append(line);
            //System.out.println("after readline loop: sec = " + System.currentTimeMillis() / 1000 + "; length = " + string_builder.length());
        } catch (SocketTimeoutException e) {
            exception_occurred = true;
            exception_msg = "Socket Timeout Exception";
            if (BuildConfig.DEBUG)
                System.out.println("exception: " + e.toString());
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
        if (BuildConfig.DEBUG) {
            System.out.println("response: " + http_query_rsp);
            System.out.println("sec = " + System.currentTimeMillis() / 1000 + "; length = " + http_query_rsp.length());
        }
    }
}
