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

import android.app.AlertDialog;
import android.app.ListActivity;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.CountDownTimer;
import android.support.v4.app.NavUtils;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.SurfaceView;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;

import org.ethereum.core.Transaction;

/*
import org.ethereum.core.Block;
import org.ethereum.core.TransactionReceipt;
import org.ethereum.facade.Ethereum;
import org.ethereum.facade.EthereumFactory;
import org.ethereum.listener.EthereumListenerAdapter;
*/

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import static org.ethereum.crypto.HashUtil.sha3;
import static org.ethereum.util.ByteUtil.longToBytesNoLeadZeroes;



public class HistoryActivity extends AppCompatActivity implements HTTP_Query_Client {
  //not: extends ListActivity
  private FrameLayout overlay_frame_layout;
  private HistoryActivity context;
  private static final float WEI_PER_ETH = (float)1000000000000000000.0;
  private static final int GAS_LIMIT = 35000;
  private SharedPreferences preferences;
  private ListView list_view;
  List<Transaction_Info> transaction_list = null;
  private int total_transactions_processed = 0;
  private Toast toast = null;

  //inputs
  private String acct_addr = "";
  private boolean show_sent = true;
  private boolean show_received = true;


  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    overlay_frame_layout = new FrameLayout(getApplicationContext());
    setContentView(overlay_frame_layout);
    View activity_history_view = getLayoutInflater().inflate(R.layout.activity_history, overlay_frame_layout, false);
    setContentView(activity_history_view);
    Toolbar toolbar = (Toolbar)findViewById(R.id.toolbar);
    //The internal implementation of the support library just checks if the Toolbar has a title (not null) at the moment the SupportActionBar is
    //set up. If there is, then this title will be used instead of the window title. You can then set a dummy title while you load the real title.
    toolbar.setTitle("");
    toolbar.setBackgroundResource(R.color.color_toolbar);
    setSupportActionBar(toolbar);
    //
    context = this;
    String app_uri = getResources().getString(R.string.app_uri);    
    preferences = getSharedPreferences(app_uri, MODE_PRIVATE);
    acct_addr = preferences.getString("acct_addr", acct_addr);
    show_sent = getIntent().getBooleanExtra("SHOW_SENT", false);
    show_received = getIntent().getBooleanExtra("SHOW_RECEIVED", false);
    (toast = Toast.makeText(context, "retrieving transactions...", Toast.LENGTH_LONG)).show();
    String app_name = getResources().getString(R.string.app_name);
    toolbar.setTitle(app_name);
    toolbar.setSubtitle(show_sent ? "Payments Sent" : "Payments Received");
  }

  public boolean onCreateOptionsMenu(Menu menu) {
    //no options menu
    return(false);
  }

  public void onResume() {
    super.onResume();  // Always call the superclass method first
    total_transactions_processed = 0;
    get_transactions(0);
  }

  //returns number of transactions processed (not number added to list)
  private int set_transactions(String rsp) {
    //typical response id:
    //{
    //  "status": 1,
    //  "data": [
    //	 {
    //	   "hash": "0x1b3dca103e0605b45f81eede754401df4082b87c4faf3f1205755b36f1b34ddf",
    //	   "sender": "0x8b8a571730b631f58e7965d78582eae1b0417ab6",
    //	   "recipient": "0x85d9147b0ec6d60390c8897244d039fb55b087c6",
    //	   "accountNonce": "76",
    //	   "price": 25000000000,
    //	   "gasLimit": 35000,
    //	   "amount": 2000000108199936,
    //	   "block_id": 2721132,
    //	   "time": "2016-11-30T09:58:07.000Z",
    //	   "newContract": 0,
    //	   "isContractTx": null,
    //	   "blockHash": "0x5c1118c94176902cab1783f8d4f8d17544c7a16c8ef377f674fa89693eb3ab0c",
    //	   "parentHash": "0x1b3dca103e0605b45f81eede754401df4082b87c4faf3f1205755b36f1b34ddf",
    //	   "txIndex": null,
    //	   "gasUsed": 21000,
    //	   "type": "tx"
    //	   },
    //	   .....
    //   }
    int idx = 0;
    int status = 0;
    int no_transactions = 0;
    if (rsp.contains("status")) {
      int field_idx = rsp.indexOf("status") + "status".length();
      int beg_idx = rsp.indexOf(':', field_idx) + 1;
      int end_idx = rsp.indexOf(',', beg_idx);
      status = Integer.valueOf(rsp.substring(beg_idx, end_idx).trim());
      idx = end_idx + 1;
    }
    if (status != 1) {
      Util.show_err(getBaseContext(), "error retrieving transactions!", 3);
      Util.show_err(getBaseContext(), rsp.substring(0, 30), 10);
      return(0);
    }
    rsp = rsp.substring(idx);
    idx = 0;
    for (int i = 0; i < 100; ++i) {
      if (rsp.contains("{")) {
        idx = rsp.indexOf('{') + 1;
        rsp = rsp.substring(idx);
      } else {
        break;
      }
      ++no_transactions;
      String txid = "";
      String from = "";
      String to = "";
      long nonce = 0;
      float size = 0;
      Calendar date = Calendar.getInstance();
      if (rsp.contains("hash")) {
        int field_idx = rsp.indexOf("hash") + "hash".length();
        int beg_idx = rsp.indexOf(':', field_idx) + 1;
        int end_idx = rsp.indexOf(',', beg_idx);
        txid = rsp.substring(beg_idx, end_idx).replaceAll("\"", "").trim();
      }
      if (rsp.contains("sender")) {
        int field_idx = rsp.indexOf("sender") + "sender".length();
        int beg_idx = rsp.indexOf(':', field_idx) + 1;
        int end_idx = rsp.indexOf(',', beg_idx);
        from = rsp.substring(beg_idx, end_idx).replace("\"", "").trim();
        if (!show_sent && from.equals(acct_addr))
          continue;
      }
      if (rsp.contains("recipient")) {
        int field_idx = rsp.indexOf("recipient") + "recipient".length();
        int beg_idx = rsp.indexOf(':', field_idx) + 1;
        int end_idx = rsp.indexOf(',', beg_idx);
        to = rsp.substring(beg_idx, end_idx).replace("\"", "").trim();
        if (!show_received && to.equals(acct_addr))
          continue;
      }
      if (rsp.contains("accountNonce")) {
        int field_idx = rsp.indexOf("accountNonce") + "accountNonce".length();
        int beg_idx = rsp.indexOf(':', field_idx) + 1;
        int end_idx = rsp.indexOf(',', beg_idx);
        nonce = Long.valueOf(rsp.substring(beg_idx, end_idx).replace("\"", "").trim());
      }
      if (rsp.contains("amount")) {
        int field_idx = rsp.indexOf("amount") + "amount".length();
        int beg_idx = rsp.indexOf(':', field_idx) + 1;
        int end_idx = rsp.indexOf(',', beg_idx);
        long wei_amount = Long.valueOf(rsp.substring(beg_idx, end_idx).trim());
        size = wei_amount / WEI_PER_ETH;
      }
      if (rsp.contains("time")) {
        int field_idx = rsp.indexOf("time") + "time".length();
        int beg_idx = rsp.indexOf(':', field_idx) + 1;
        int end_idx = rsp.indexOf(',', beg_idx);
        String date_str = rsp.substring(beg_idx, end_idx).replace("\"", "").trim();
        //System.out.println("raw date: " + created_at);
        //could the date string have more or less than 3 digits after the decimal? (eg 5: 2015-11-10T01:20:14.16525Z). to avoid
        //complication, (and parse exceotions) we always force 3.
        int decimal_idx = date_str.indexOf(".");
        String conforming_date_str = date_str;
        if (decimal_idx < 0)
          System.out.println("hey! no decimal in date string: " + conforming_date_str);
        if (decimal_idx >= 0) {
          //decimal_idx is idx of decimal point. add 1 to get len, and we need 3 more digits (followed by 'Z')
          if (date_str.length() >= decimal_idx + 5)
            //substring includes end_idx -1
            conforming_date_str = date_str.substring(0, decimal_idx + 4) + "Z";
          else
            conforming_date_str = date_str.substring(0, decimal_idx) + ".000Z";
        }
        //in java 7, an 'X' in the dateformat indicates that the date string might contain an ISO8601 suffix, eg. Z for UTC.
        //unfortunately that doesn't work for android. a 'Z' in the dateformat indicates that the timezone conforms to the RFC822
        //time zone standard, e.g. -0800. so we'll just convert to that.
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
        conforming_date_str = conforming_date_str.replaceAll("Z$", "+0000");
        try {
          date.setTime(df.parse(conforming_date_str));
        } catch (ParseException e) {
          System.out.println(e.toString());
        }
      }
      Transaction_Info transaction_info = new Transaction_Info(txid, to, from, nonce, size, date);
      transaction_list.add(transaction_info);
      if (rsp.contains("}")) {
        idx = rsp.indexOf('}') + 1;
        rsp = rsp.substring(idx);
      } else {
        break;
      }
    }
    return(no_transactions);
  }


  public void handle_http_rsp(String callback, String rsp) {
    if (callback.equals("transactions")) {
      int processed = set_transactions(rsp);
      total_transactions_processed += processed;
      if ((processed) >= 50) {
        System.out.println(processed + " transaction processed; cur size is " + transaction_list.size());
        if (toast != null)
          toast.cancel();
        (toast = Toast.makeText(context, "retrieving " + total_transactions_processed + " transactions...", Toast.LENGTH_LONG)).show();
        get_transactions(total_transactions_processed);
      } else {
        Transaction_Info values[] = transaction_list.toArray(new Transaction_Info[transaction_list.size()]);
        list_view = (ListView) findViewById(R.id.listview);
        Transaction_Array_Adapter transaction_array_adapter = new Transaction_Array_Adapter(context, values, acct_addr);
        list_view.setAdapter(transaction_array_adapter);
        /*
        list_view.setOnItemClickListener(new AdapterView.OnItemClickListener()
        {
        @Override
        public void onItemClick(AdapterView<?> arg0, View arg1, int arg2,long arg3)
        {
            //args2 is the listViews Selected index
          }
        });
        */
        //in case the "retrieving transactions" message is still showing
        if (toast != null)
          toast.cancel();
      }
      return;
    }
  }


  //offset is the current nonce minus the index of the starting (most recent) transaction. for example, if the current nonce of the
  //most recent transaction is 77, then to get transactions with nonces from 77 to 28, pass offset "0". to get transactions with
  //nonces 76 to 27, pass offset "1"
  private void get_transactions(int offset) {
    if (offset == 0)
      transaction_list = new ArrayList<Transaction_Info>();
    String transactions_URL = "https://etherchain.org/api/account/" + acct_addr + "/tx/" + offset;
    String parms[] = new String[2];
    parms[0] = transactions_URL;
    parms[1] = "transactions";
    new HTTP_Query_Task(this, context).execute(parms);
  }


}
