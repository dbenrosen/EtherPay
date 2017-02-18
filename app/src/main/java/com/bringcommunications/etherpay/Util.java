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

import android.app.ActivityManager;
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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static android.content.Context.ACTIVITY_SERVICE;

/**
 * Created by dbrosen on 11/25/16.
 */

public class Util {


        public static final long WEI_PER_SZABO = 1000000000000L;
        public static final long WEI_PER_FINNEY = 1000000000000000L;
        public static final long WEI_PER_ETH = 1000000000000000000L;
        public static final long DEFAULT_GAS_LIMIT = 35000;
        public static final long DEFAULT_GAS_PRICE = 2000000;

  static public void show_err(Context context, String msg, int seconds) {
        //toast LENGTH_LONG is 3.5 secs. (and LENGTH_SHORT is 2.5) we need to display this about 10 secs
        final Toast tag = Toast.makeText(context, msg, Toast.LENGTH_SHORT);
        tag.show();
        new CountDownTimer(1000 * seconds, 1000) {
            public void onTick(long millisUntilFinished) { tag.show(); }
            public void onFinish()                       { tag.show(); }
        }.start();
    }

        //WARNING!!
        //this is a very cheesy, hacky parser!
        //you can't have several keywards with partial matches. for example:
        // { keyword: 7, key: 6 }
        // if you try to get the value for key, you'll get 7!!
        //BEWARE!!
      static public String json_parse(String json, String field) {
        String value = "";
        boolean done = false;
        int idx = 0;
        try {
            final Pattern pattern = Pattern.compile("[,}]");
            do {
                int quote_idx = json.indexOf('"', idx);
                int field_idx = json.indexOf(field, idx);
                if (quote_idx >= 0 && quote_idx < field_idx) {
                    int match_quote_idx = json.indexOf('"', quote_idx + 1);
                    if (match_quote_idx >= 0) {
                        String quoted_field = json.substring(quote_idx, match_quote_idx).replace("\"", "").trim();
                        if (!quoted_field.equals(field)) {
                            idx = match_quote_idx + 1;
                            continue;
                        }
                    } else {
                        System.out.println("unmatched quotes! json: " + json);
                        System.out.println("field: " + field);
                        System.out.println("field_idx = " + field_idx + ", quote_idx = " + quote_idx);
                    }
                }
                //System.out.println("field_idx = " + field_idx);
                done = true;
                if (field_idx >= 0) {
                    field_idx += field.length();
                    int beg_idx = json.indexOf(':', field_idx) + 1;
                    //System.out.println("beg = " + beg_idx);
                    Matcher matcher = pattern.matcher(json.substring(beg_idx));
                    if (matcher.find()) {
                        quote_idx = json.indexOf('"', beg_idx);
                        int end_idx = beg_idx + matcher.start();
                        if (quote_idx >= 0 && quote_idx < end_idx) {
                            //found a quote somewhere in the string. we stipulate that it must be at the beginning of the string
                            int match_quote_idx = json.indexOf('"', quote_idx + 1);
                            if (match_quote_idx >= 0) {
                                beg_idx = quote_idx + 1;
                                end_idx = match_quote_idx;
                                //System.out.println("quoted string: " + json.substring(beg_idx, end_idx));
                            } else {
                                System.out.println("unmatched quotes! json: " + json);
                                System.out.println("field: " + field);
                                System.out.println("field_idx = " + field_idx + ", beg_idx = " + beg_idx + ", quote_idx = " + quote_idx);
                            }
                        }
                        //System.out.println("end = " + end_idx);
                        value = json.substring(beg_idx, end_idx).replace("\"", "").trim();
                        //System.out.println("value = " + value);
                    } else {
                        System.out.println("malformed json:" + json);
                    }
                }
            } while (!done);
        } catch (Exception e) {
            System.out.println(e.toString());
            e.printStackTrace();
        }
        return(value);
      }


  static public long get_avail_memory_kb(Context context) {
          ActivityManager activity_manager = (ActivityManager)context.getSystemService(ACTIVITY_SERVICE);
          ActivityManager.MemoryInfo mem_info = new ActivityManager.MemoryInfo();
          activity_manager.getMemoryInfo(mem_info);
          long kb_avail = mem_info.availMem / 1000;
          System.out.println("memmory: " + kb_avail + " kB");
          return kb_avail;
      }


      static public long balance_wei_from_json(Context context, String rsp) {
        //typical response is:
            //{
            // "status": 1,
            // "data": [
            //  {
            //   "address": "0x7223efbf783eba259451a89e8e84c26611df8c4f",
            //   "balance": 40038159108626850000,
            //   "nonce": null,
            //   "code": "0x",
            //   "name": null,
            //   "storage": null,
            //   "firstSeen": null
            //  }
            // ]
            //}
        long balance = -1;
        boolean got_balance = false;
        String balance_str = Util.json_parse(rsp, "balance");
        if (!balance_str.isEmpty() && !balance_str.equals("null")) {
          //System.out.println(rsp);
          balance = Long.valueOf(balance_str);
          got_balance = true;
        } else if (rsp.contains("status")) {
          String status_str = Util.json_parse(rsp, "status");
          if (status_str.equals("1")) {
            //no error, but no balance data.... the account has never been used
            balance = 0;
            got_balance = true;
          }
        }
        if (!got_balance) {
          Util.show_err(context, "error retrieving balance!", 3);
          Util.show_err(context, rsp, 10);
        }
        //Toast.makeText(context, "balance = " + balance, Toast.LENGTH_LONG).show();
        return(balance);
      }

  
}
