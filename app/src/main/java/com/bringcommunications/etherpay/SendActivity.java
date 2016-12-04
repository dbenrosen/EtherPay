package com.bringcommunications.etherpay;

import android.app.AlertDialog;
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
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
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



public class SendActivity extends AppCompatActivity implements HTTP_Query_Client {

  private FrameLayout overlay_frame_layout;
  private SendActivity context;
  private static final float WEI_PER_ETH = (float)1000000000000000000.0;
  private static final int GAS_LIMIT = 35000;
  private SharedPreferences preferences;
  private Hex hex;
  //inputs
  private String acct_addr = "";
  private String private_key = "";
  private String to_addr = "";
  private String auto_pay = "";
  private float size;
  private String data = "";
  private float balance;
  private float price;
  //we set these
  private long gas_price;
  private long nonce;
  private String txid;
  
  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    overlay_frame_layout = new FrameLayout(getApplicationContext());
    setContentView(overlay_frame_layout);
    View activity_send_view = getLayoutInflater().inflate(R.layout.activity_send, overlay_frame_layout, false);
    setContentView(activity_send_view);
    Toolbar toolbar = (Toolbar)findViewById(R.id.toolbar);
    toolbar.setTitle("EtherPay - Send Payment");
    toolbar.setBackgroundResource(R.color.etherpay_blue);
    setSupportActionBar(toolbar);
    //
    context = this;
    hex = new Hex();
    preferences = getSharedPreferences("etherpay.bringcommunications.com", MODE_PRIVATE);
    private_key = preferences.getString("key", private_key);
    acct_addr = preferences.getString("acct_addr", acct_addr);
    balance = preferences.getFloat("balance", balance);
    price = preferences.getFloat("price", price);
    auto_pay = getIntent().getStringExtra("AUTO_PAY");
    to_addr = getIntent().getStringExtra("TO_ADDR");
    TextView to_addr_view = (TextView) findViewById(R.id.to_addr);
    //String to_addr_str = to_addr.substring(0, 20) + " ...";
    to_addr_view.setText(to_addr);
    String size_str = getIntent().getStringExtra("SIZE");
    size = Float.valueOf(size_str);
    TextView size_view = (TextView) findViewById(R.id.size);
    size_str = String.format("%1.03f", size);
    size_view.setText(size_str);
    data = getIntent().getStringExtra("DATA");
    if (!data.isEmpty()) {
      EditText data_view = (EditText) findViewById(R.id.data);
      data_view.setText(data);
    }
    //sanity check
    if (to_addr.length() != 42) {
      this.finish();
    }
  }

  public boolean onCreateOptionsMenu(Menu menu) {
    //no options menu
    return(false);
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
    TextView txid_view = (TextView) findViewById(R.id.txid);
    txid_view.setText(txid);
    if (auto_pay.equals("true")) {
      String msg = txid.isEmpty() ? "transaction failed" : "your transaction was sent successfully";
      Toast.makeText(context, msg, Toast.LENGTH_LONG).show();
      NavUtils.navigateUpFromSameTask(context);
      this.finish();
    } else {
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
      long last_pay_sec = System.currentTimeMillis() / 1000;
      SharedPreferences.Editor preferences_editor = preferences.edit();
      preferences_editor.putLong("last_pay_sec", last_pay_sec);
      preferences_editor.putBoolean("refresh_mode", true);
      preferences_editor.putLong("last_nonce", nonce);
      preferences_editor.apply();
    }
  }

    public void do_data_help(View view) {
      String help_text = "Use this field to attach identifting information to this payment. For example, " +
              "if you are paying a bill at a restaurant, you might enter your table number here.";
      //Toast.makeText(context, help_text, Toast.LENGTH_LONG).show();
      Util.show_err(getBaseContext(), help_text, 5);
    }

    public void do_pay(View view) {
      //validate size... we check for sufficient balance later...
      EditText size_view = (EditText) findViewById(R.id.size);
      size = Float.valueOf(size_view.getText().toString());
      if (size == 0) {
          Toast.makeText(context, "Cannot send zero ETH", Toast.LENGTH_LONG).show();
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
      send_to_0();
  }

  public void handle_http_rsp(String callback, String rsp) {
    if (callback.equals("gas")) {
      set_gas(rsp);
      float max_gas = (GAS_LIMIT * gas_price) / WEI_PER_ETH;
      if (size + max_gas > balance) {
        String balance_str = String.format("%1.06f", balance);
        String size_str = String.format("%1.06f", size);
        String gas_str = String.format("%1.08f", max_gas);
        String msg = "Balance (" + balance_str + ") is not sufficient to cover " +  size_str + " ETH, plus " + gas_str + " GAS";
        Toast.makeText(context, msg, Toast.LENGTH_LONG).show();
        return;
      }
      send_to_1();
    } else if (callback.equals("nonce")) {
      set_nonce(rsp);
      if (nonce < 0) {
        //error occurred. dsp_txid will show error message and exit
        dsp_txid_and_exit();
      }
      send_to_2();
    } else if (callback.equals("broadcast")) {
      set_txid(rsp);
      dsp_txid_and_exit();
    }
  }

  private void set_gas(String gas_rsp) {
    //typical response id:
    //{
    // "status": 1,
    // "data": [
    //  {
    //   "price": 23000000000
    //  }
    // ]
    //}
    gas_price = 0;
    if (gas_rsp.contains("price")) {
      int field_idx = gas_rsp.indexOf("price") + "price".length();
      int beg_idx = gas_rsp.indexOf(':', field_idx) + 1;
      int end_idx = gas_rsp.indexOf('}', beg_idx);
      String gas_price_str = gas_rsp.substring(beg_idx, end_idx).trim();
      if (!gas_price_str.equals("null"))
      	gas_price = Long.valueOf(gas_price_str);
      float gas_price_eth = gas_price / WEI_PER_ETH;
      //Toast.makeText(context, "gas_price = " + String.format("%11.09f", gas_price_eth) + " ETH", Toast.LENGTH_LONG).show();
    } else {
      Util.show_err(getBaseContext(), "error retreiving gas price!", 3);
      Util.show_err(getBaseContext(), gas_rsp, 10);
    }
  }

  private void set_nonce(String nonce_rsp) {
    //typical response id:
    //{
    // "status": 1,
    // "data": [
    //  {
    //   "accountNonce": "16"
    //  }
    // ]
    //}
    nonce = -1;
    if (nonce_rsp.contains("accountNonce")) {
      nonce_rsp = nonce_rsp.replaceAll("\"", "");
      int field_idx = nonce_rsp.indexOf("accountNonce") + "accountNonce".length();
      int beg_idx = nonce_rsp.indexOf(':', field_idx) + 1;
      int end_idx = nonce_rsp.indexOf('}', beg_idx);
      String nonce_str = nonce_rsp.substring(beg_idx, end_idx).trim();
      if (!nonce_str.equals("null"))
    	nonce = Long.valueOf(nonce_str);
      //Toast.makeText(context, "nonce = " + nonce, Toast.LENGTH_LONG).show();
    } else {
      Util.show_err(getBaseContext(), "error retreiving nonce!", 3);
      Util.show_err(getBaseContext(), nonce_rsp, 10);
    }
  }

  private void set_txid(String broadcast_rsp) {  
    //typical response id:
    //{
    //  "jsonrpc": "2.0",
    //  "result": "0xd22456131597cff2297d1034f9e6f790e9678d85c041591949ab5a8de5f73f04",
    //  "id": 1
    //}
    txid = "";
    if (broadcast_rsp.contains("result")) {
      broadcast_rsp = broadcast_rsp.replaceAll("\"", "");
      int field_idx = broadcast_rsp.indexOf("result") + "result".length();
      int beg_idx = broadcast_rsp.indexOf(':', field_idx) + 1;
      int end_idx = broadcast_rsp.indexOf(',', beg_idx);
      txid = broadcast_rsp.substring(beg_idx, end_idx).trim();
      //System.out.println("txid: " + txid);
    } else {
      Util.show_err(getBaseContext(), "error broadcasting transaction!", 3);
      Util.show_err(getBaseContext(), broadcast_rsp, 10);
    }
    //Toast.makeText(context, "txid = " + txid, Toast.LENGTH_LONG).show();
  }

    
  //step 0 in send -- get gas price
  private void send_to_0() {
    //get gas price
    String gas_URL = "https://etherchain.org/api/gasPrice";
    String parms[] = new String[2];
    parms[0] = gas_URL;
    parms[1] = "gas";
    new HTTP_Query_Task(this, context).execute(parms);
  }

  //step 1 in send -- get nonce
  private void send_to_1() {
    //get account nonce
    String nonce_URL = "https://etherchain.org/api/account/" + acct_addr + "/nonce";
    String parms[] = new String[2];
    parms[0] = nonce_URL;
    parms[1] = "nonce";
    new HTTP_Query_Task(this, context).execute(parms);
  }

  //step 2 in send -- just call create_and_broadcast_transaction passing the user's transaction data
  private void send_to_2() {
    float max_gas = (GAS_LIMIT * gas_price) / WEI_PER_ETH;
    ++nonce;
    create_and_broadcast_transaction(gas_price, nonce, to_addr, data, size, "broadcast");
  }


  private void create_and_broadcast_transaction(long gas_price, long nonce, String to_addr, String data, float size, String callback) {
    //this is sufficient for simple transactions, even if they include a little data. it is ~0.01 cents
    long gas_limit = GAS_LIMIT;
    long wei = (long)(size * WEI_PER_ETH);
    String to_addr_no_0x = to_addr.startsWith("0x") ? to_addr.substring(2) : to_addr;
    //create signed transaction
    byte[] bytes_key = null;
    byte[] bytes_to_addr = null;
    try {
      bytes_key = hex.decode(private_key.getBytes());
      bytes_to_addr = hex.decode(to_addr_no_0x.getBytes());
    } catch (DecoderException e) {
      Util.show_err(getBaseContext(), e.toString(), 15);
    }
    byte[] bytes_gas_price = longToBytesNoLeadZeroes(gas_price);
    byte[] bytes_gas_limit = longToBytesNoLeadZeroes(gas_limit);
    byte[] bytes_value = longToBytesNoLeadZeroes(wei);
    byte[] bytes_nonce = longToBytesNoLeadZeroes(nonce);
    byte[] bytes_data = data.isEmpty() ? null : data.getBytes();
    Transaction tx = new Transaction(bytes_nonce, bytes_gas_price, bytes_gas_limit, bytes_to_addr, bytes_value, bytes_data);
    //
    tx.sign(bytes_key);
    byte[] rlp_encoded_tx = tx.getEncoded();
    String hex_tx = new String(hex.encodeHex(rlp_encoded_tx));
    String broadcast_URL = "https://api.etherscan.io/api?module=proxy&action=eth_sendRawTransaction&hex=" + hex_tx;
    String parms[] = new String[2];
    parms[0] = broadcast_URL;
    parms[1] = callback;
    new HTTP_Query_Task(this, context).execute(parms);
  }

}
