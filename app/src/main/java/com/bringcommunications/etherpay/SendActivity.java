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
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.CountDownTimer;
import android.os.Message;
import android.support.v4.app.NavUtils;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutCompat;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.SurfaceView;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Spinner;
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

import static org.ethereum.crypto.HashUtil.sha3;
import static org.ethereum.util.ByteUtil.longToBytesNoLeadZeroes;



public class SendActivity extends AppCompatActivity implements Payment_Processor_Client, AdapterView.OnItemSelectedListener {
  //HTTP_Query_Client

  private FrameLayout overlay_frame_layout;
  private SendActivity context;
  private static final float WEI_PER_ETH = (float)1000000000000000000.0;
  private SharedPreferences preferences;
  private Hex hex;
  //inputs
  private String acct_addr = "";
  private String private_key = "";
  private String to_addr = "";
  private String auto_pay = "";
  private boolean show_gas = false;
  private boolean show_data = false;
  private boolean send_is_done = false;
  private float eth_size;
  private float eth_balance;
  private float price;
  //we set these
  private long gas_limit = Util.DEFAULT_GAS_LIMIT;
  private String data = "";
  private long nonce;
  private String txid = "";
  private enum Denomination { ETH, FINNEY };
  private Denomination denomination = Denomination.ETH;
  private Toast toast = null;


  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    overlay_frame_layout = new FrameLayout(getApplicationContext());
    setContentView(overlay_frame_layout);
    //
    context = this;
    hex = new Hex();
    preferences = getSharedPreferences("etherpay.bringcommunications.com", MODE_PRIVATE);
    private_key = preferences.getString("key", private_key);
    acct_addr = preferences.getString("acct_addr", acct_addr);
    long wei_balance = preferences.getLong("balance", 0);
    eth_balance = (float)wei_balance / Util.WEI_PER_ETH;
    price = preferences.getFloat("price", price);
    show_gas = preferences.getBoolean("show_gas", show_gas);
    show_data = preferences.getBoolean("show_data", show_data);
    boolean denomination_eth = preferences.getBoolean("denomination_eth", true);
    System.out.println("denomination_eth is " + denomination_eth);
    denomination = denomination_eth ? Denomination.ETH : Denomination.FINNEY;
    auto_pay = getIntent().getStringExtra("AUTO_PAY");
    to_addr = getIntent().getStringExtra("TO_ADDR");
    String size_str = getIntent().getStringExtra("SIZE");
    eth_size = Float.valueOf(size_str);
    data = getIntent().getStringExtra("DATA");
    send_is_done = false;
    //
    View activity_send_view = getLayoutInflater().inflate(R.layout.activity_send, overlay_frame_layout, false);
    setContentView(activity_send_view);
    //
    Spinner dropdown = (Spinner)findViewById(R.id.denomination);
    String[] items = new String[] {"ETH", "Finney"};
    ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, items);
    dropdown.setSelection((denomination == Denomination.ETH) ? 0 : 1);
    dropdown.setOnItemSelectedListener(this);
    dropdown.setAdapter(adapter);
    //
    Toolbar toolbar = (Toolbar)findViewById(R.id.toolbar);
    String app_name = getResources().getString(R.string.app_name);    
    toolbar.setTitle(app_name);
    toolbar.setSubtitle("Send Payment");
    toolbar.setBackgroundResource(R.color.color_toolbar);
    setSupportActionBar(toolbar);
    //
    TextView to_addr_view = (TextView) findViewById(R.id.to_addr);
    to_addr_view.setText(to_addr);
    //
    TextView size_view = (TextView) findViewById(R.id.size);
    size_str = (denomination == Denomination.ETH) ? String.format("%1.03f", eth_size) : String.format("%03d", (int)(eth_size * 1000 + 0.5));
    size_view.setText(size_str);
    //
    LinearLayout gas_layout = (LinearLayout) findViewById(R.id.gas_layout);
    TextView gas_prompt_view = (TextView) findViewById(R.id.gas_prompt);
    TextView gas_view = (TextView) findViewById(R.id.gas);
    ImageButton gas_help_view = (ImageButton) findViewById(R.id.gas_help);
    if (show_gas) {
      String gas_str = String.format("%7d", gas_limit);
      gas_view.setText(gas_str);
      gas_layout.setVisibility(View.VISIBLE);
      gas_prompt_view.setVisibility(View.VISIBLE);
      gas_help_view.setVisibility(View.VISIBLE);
      gas_view.setVisibility(View.VISIBLE);
    } else {
      gas_layout.setVisibility(View.GONE);
      gas_prompt_view.setVisibility(View.GONE);
      gas_help_view.setVisibility(View.GONE);
      gas_view.setVisibility(View.GONE);
    }

    LinearLayout data_layout = (LinearLayout) findViewById(R.id.data_layout);
    TextView data_prompt_view = (TextView) findViewById(R.id.data_prompt);
    TextView data_view = (TextView) findViewById(R.id.data);
    ImageButton data_help_view = (ImageButton) findViewById(R.id.data_help);
    if (show_data) {
      data_view.setText(data);
      data_layout.setVisibility(View.VISIBLE);
      data_prompt_view.setVisibility(View.VISIBLE);
      data_help_view.setVisibility(View.VISIBLE);
      data_view.setVisibility(View.VISIBLE);
    } else {
      data = "";
      data_layout.setVisibility(View.GONE);
      data_prompt_view.setVisibility(View.GONE);
      data_help_view.setVisibility(View.GONE);
      data_view.setVisibility(View.GONE);
    }

    //
    //sanity check
    if (to_addr.length() != 42) {
      this.finish();
    }
  }

  //returns false => no options menu
  public boolean onCreateOptionsMenu(Menu menu) {
    MenuInflater inflater = getMenuInflater();
    inflater.inflate(R.menu.send_options, menu);
    menu.findItem(R.id.show_gas).setChecked(show_gas);
    menu.findItem(R.id.show_data).setChecked(show_data);
    return(true);
  }


  public boolean onOptionsItemSelected(MenuItem item) {
    switch (item.getItemId()) {
      case R.id.show_gas: {
        boolean show_gas = item.isChecked() ? false : true;
        item.setChecked(show_gas);
        SharedPreferences.Editor preferences_editor = preferences.edit();
        preferences_editor.putBoolean("show_gas", show_gas);
        preferences_editor.apply();
        recreate();
        return true;
      }
      case R.id.show_data: {
        boolean show_data = item.isChecked() ? false : true;
        item.setChecked(show_data);
        SharedPreferences.Editor preferences_editor = preferences.edit();
        preferences_editor.putBoolean("show_data", show_data);
        preferences_editor.apply();
        recreate();
        return true;
      }
      case R.id.help:
        do_help(R.string.send_help_title, R.string.send_help);
        return true;
      default:
        return super.onOptionsItemSelected(item);
    }
  }


  //this onItemSelected is called when a new denomination is selected from the denomination drop-down
  @Override
  public void onItemSelected(AdapterView<?> parent, View v, int position, long id) {
    EditText size_view = (EditText) findViewById(R.id.size);
    String cur_size_str = size_view.getText().toString();
    float cur_size = Float.valueOf(cur_size_str);
    eth_size = (denomination == Denomination.ETH) ? cur_size : cur_size / 1000;
    denomination = (position == 0) ? Denomination.ETH : Denomination.FINNEY;
    if (denomination == Denomination.FINNEY) {
      eth_size = (float)((int)(eth_size * 1000 + 0.5)) / 1000;
    }
    boolean denomination_eth = (denomination == Denomination.ETH) ? true : false;
    SharedPreferences.Editor preferences_editor = preferences.edit();
    preferences_editor.putBoolean("denomination_eth", denomination_eth);
    System.out.println("set denomination_eth to " + denomination_eth + ", id = " + id);
    preferences_editor.apply();
    String size_str = (denomination == Denomination.ETH) ? String.format("%1.03f", eth_size) : String.format("%03d", (int)(eth_size * 1000 + 0.5));
    size_view.setText(size_str);
  }
  public void onNothingSelected(AdapterView<?> parent) {
  }

  
  public void onResume() {
    super.onResume();  // Always call the superclass method first
    if (auto_pay.equals("true")) {
      Button pay_button = (Button) findViewById(R.id.pay_button);
      pay_button.setEnabled(false);
      TextView size_view = (TextView) findViewById(R.id.size);
      size_view.setKeyListener(null);
      size_view.setCursorVisible(false);
      size_view.setFocusable(false);
      EditText data_view = (EditText) findViewById(R.id.data);
      data_view.setKeyListener(null);
      data_view.setCursorVisible(false);
      data_view.setFocusable(false);
      do_pay(null);
    }
  }


  //displays the txid -- but also displays a message informing the user that the transaction was completed.
  //waits for the user to acknowledge the message -- AND THEN RETURNS TO THE PARENT ACTIVITY!
  private void dsp_txid_and_exit() {
    /*
    TextView txid_view = (TextView) findViewById(R.id.txid);
    txid_view.setText(txid);
    if (auto_pay.equals("true")) {
      String msg = txid.isEmpty() ? "transaction failed" : "your transaction was sent successfully";
      Toast.makeText(context, msg, Toast.LENGTH_LONG).show();
      NavUtils.navigateUpFromSameTask(context);
      this.finish();
    } else
    */
    {
      AlertDialog.Builder builder = new AlertDialog.Builder(this);
      String title = txid.isEmpty() ? "Error" : "Transaction Sent";
      String msg = txid.isEmpty() ? "An error occurred while attempting this transaction -- press OK to continue" :
              "your transaction was sent successfully -- press OK to continue";
      builder.setTitle(title);
      builder.setMessage(msg);
      builder.setCancelable(true);
      builder.setNeutralButton(android.R.string.ok,
              new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                  dialog.cancel();
                  NavUtils.navigateUpFromSameTask(context);
                  context.finish();
                }
              });
      AlertDialog alert = builder.create();
      alert.show();
    }
  }

  public void do_size_help(View view) {
    do_help(R.string.size_help_title, R.string.size_help);
  }
  public void do_gas_help(View view) {
    do_help(R.string.gas_help_title, R.string.gas_help);
  }
  public void do_data_help(View view) {
    do_help(R.string.data_help_title, R.string.data_help);
  }

  public void do_pay(View view) {
      if (send_is_done)
        return;
      //validate size... we check for sufficient balance later...
      EditText size_view = (EditText) findViewById(R.id.size);
      String user_size_str = size_view.getText().toString();
      float user_size = Float.valueOf(user_size_str);
      eth_size = (denomination == Denomination.ETH) ? user_size : user_size / 1000;
      if (eth_size == 0) {
          Toast.makeText(context, "Cannot send zero ETH", Toast.LENGTH_LONG).show();
          return;
      }
      if (show_gas) {
        TextView gas_view = (TextView) findViewById(R.id.gas);
        String gas_limit_str = gas_view.getText().toString().trim();
        try {
          gas_limit = Long.valueOf(gas_limit_str);
        } catch (NumberFormatException e) {
          Toast.makeText(context, "Unable to parse Gas limit: " + gas_limit_str, Toast.LENGTH_LONG).show();
          return;
        }
      }
      if (gas_limit < Util.DEFAULT_GAS_LIMIT) {
        Toast.makeText(context, "Gas limit is too low -- transaction might not succeed!", Toast.LENGTH_LONG).show();
        return;
      }
      //ensure sufficient funds
      long gas_price = preferences.getLong("gas_price", Util.DEFAULT_GAS_PRICE);
      float max_gas_eth = (gas_limit * gas_price) / WEI_PER_ETH;
      if (eth_size + max_gas_eth > eth_balance) {
        String balance_str = String.format("%1.06f", eth_balance);
        String size_str = String.format("%1.06f", eth_size);
        String gas_str = String.format("%1.08f", max_gas_eth);
        String msg = "Balance (" + balance_str + ") is not sufficient to cover " +  size_str + " ETH, plus " + gas_str + " GAS";
        Toast.makeText(context, msg, Toast.LENGTH_LONG).show();
        return;
      }
      //validate to_addr
      if (!to_addr.startsWith("0x") || to_addr.length() != 42) {
        Toast.makeText(context, "Recipient address is not valid; length is " + to_addr.length(), Toast.LENGTH_LONG).show();
        return;
      }
      if (to_addr.equals(acct_addr)) {
        Toast.makeText(context, "Recipient cannot be the same as your account address", Toast.LENGTH_LONG).show();
        return;
      }
      EditText data_view = (EditText) findViewById(R.id.data);
      data = data_view.getText().toString();
      long size_wei = (long)(eth_size * WEI_PER_ETH);
      Payment_Processor.send(this, context, "", to_addr, size_wei, gas_limit, data.getBytes(), false);
      send_is_done = true;
      Button pay_view = (Button) findViewById(R.id.pay_button);
      pay_view.setClickable(false);
  }


  private void do_help(int title, int msg) {
    android.support.v7.app.AlertDialog.Builder alertDialogBuilder = new android.support.v7.app.AlertDialog.Builder(context);
    alertDialogBuilder.setTitle(getResources().getString(title));
    alertDialogBuilder.setMessage(getResources().getString(msg));
    alertDialogBuilder.setCancelable(false);
    alertDialogBuilder.setNeutralButton("OK",
            new DialogInterface.OnClickListener() {
              public void onClick(DialogInterface dialog,int id) {
                dialog.cancel();
              }
            });
    android.support.v7.app.AlertDialog alertDialog = alertDialogBuilder.create();
    alertDialog.show();
  }


  //is is the callback from Payment_Processor
  public boolean payment_result(boolean ok, String txid, long size_wei, String client_data, String error) {
    this.txid = txid;
    dsp_txid_and_exit();
    return true;
  }
  public void interim_payment_result(long size_wei, String client_data, String msg) {
    /*
    TODO:
    Message delay_message = socket_emitter_handler.obtainMessage(SOCKET_EVENT_DELAY, 1, 0, null);
    delay_message.sendToTarget();
    if (toast != null)
      toast.cancel();
    (toast = Toast.makeText(context, "processing", Toast.LENGTH_LONG)).show();
     */
  }
  public void balance_result(boolean ok, long balance, String error) {
    System.out.println("SendActivity:balance_result: Hey! we should never be here!");
  }
  public void interim_balance_result(String msg) {
    System.out.println("SendActivity:interim_result: Hey! we should never be here!");
  }

}
