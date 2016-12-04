  package com.bringcommunications.etherpay;

/*
  overview of vars stored in preferences:

  balance          => most recent balance of current acct
  price            => most recent price of Ether in USD
  last_refresh_sec => timestamp for last time we attempted to update nonce, price, balance
  last_nonce_sec   => timestamp for last time we updated balance
  last_price_sec   => timestamp for last time we updated price
  last_balance_sec => timestamp for last time we updated balance
  last_pay_sec     => timestamp for last time we completed a payment
  private_key      => current account's private key
  acct_addr        => current account address
  last_nonce       => nonce used in last payment
  refresh_mode     => mode is set after a payment completes, until we refresh balance
*/


  import android.content.DialogInterface;
  import android.content.Intent;
  import android.content.SharedPreferences;
  import android.graphics.Color;
  import android.os.AsyncTask;
  import android.os.Bundle;
  import android.os.CountDownTimer;
  import android.support.v7.app.AlertDialog;
  import android.support.v7.app.AppCompatActivity;
  import android.support.v7.widget.Toolbar;
  import android.view.Menu;
  import android.view.MenuInflater;
  import android.view.MenuItem;
  import android.view.View;
  import android.view.animation.AlphaAnimation;
  import android.view.animation.Animation;
  import android.widget.FrameLayout;
  import android.widget.ImageButton;
  import android.widget.TextView;
  import android.widget.Toast;

  import org.ethereum.crypto.ECKey;
  import org.spongycastle.util.encoders.Hex;

  import java.io.BufferedInputStream;
  import java.io.BufferedReader;
  import java.io.IOException;
  import java.io.InputStream;
  import java.io.InputStreamReader;
  import java.io.UnsupportedEncodingException;
  import java.math.BigInteger;
  import java.net.HttpURLConnection;
  import java.net.MalformedURLException;
  import java.net.URL;
  import java.util.Random;

  import static android.support.v7.appcompat.R.styleable.AlertDialog;


  public class MainActivity extends AppCompatActivity implements HTTP_Query_Client {

    private static final float WEI_PER_ETH = (float)1000000000000000000.0;

    private String acct_addr = "";
    private String private_key = "";
    private float balance = 0;
    private float price = 0;
    private long last_refresh_sec = 0;
    private long last_nonce_sec = 0;
    private long last_price_sec = 0;
    private long last_balance_sec = 0;
    private long last_pay_sec = 0;
    private long last_nonce = 0;
    private long nonce = -1;
    private boolean refresh_mode = false;
    private SharedPreferences preferences;
    private FrameLayout overlay_frame_layout;
    private View activity_main_view;
    private MainActivity context;
    private Toast toast = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
      super.onCreate(savedInstanceState);
      overlay_frame_layout = new FrameLayout(getApplicationContext());
      setContentView(overlay_frame_layout);
      View activity_main_view = getLayoutInflater().inflate(R.layout.activity_main, overlay_frame_layout, false);
      setContentView(activity_main_view);
      Toolbar toolbar = (Toolbar)findViewById(R.id.toolbar);
      toolbar.setBackgroundResource(R.color.etherpay_blue);
      setSupportActionBar(toolbar);
      context = this;
      preferences = getSharedPreferences("etherpay.bringcommunications.com", MODE_PRIVATE);
    }

    //returns false => no options menu
    public boolean onCreateOptionsMenu(Menu menu) {
      MenuInflater inflater = getMenuInflater();
      inflater.inflate(R.menu.main_options, menu);
      return(true);
    }

    public boolean onOptionsItemSelected(MenuItem item) {
      switch (item.getItemId()) {
         case R.id.import_account:
            if (ask_delete_old_key())
              delete_old_key_import_new();
            return true;
        case R.id.welcome:
          show_welcome_dialog();
          return true;
        case R.id.about:
          show_about_dialog();
          return true;
        default:
          return super.onOptionsItemSelected(item);
      }
    }

    public void onPause() {
        super.onPause();  // Always call the superclass method first
        SharedPreferences.Editor preferences_editor = preferences.edit();
        preferences_editor.putFloat("balance", balance);
        preferences_editor.putFloat("price", price);
        preferences_editor.putLong("refresh-sec", last_refresh_sec);
        preferences_editor.putLong("last_pay_sec", last_pay_sec);
        preferences_editor.putBoolean("refresh_mode", false);
        preferences_editor.commit();
    }

    public void onResume() {
        super.onResume();  // Always call the superclass method first
        balance = preferences.getFloat("balance", balance);
        price = preferences.getFloat("price", price);
        last_refresh_sec = preferences.getLong("refresh-sec", last_refresh_sec);
        last_pay_sec = preferences.getLong("last_pay_sec", last_pay_sec);
        private_key = preferences.getString("key", private_key);
        acct_addr = preferences.getString("acct_addr", "");
        last_nonce = preferences.getLong("last_nonce", last_nonce);
        refresh_mode = preferences.getBoolean("refresh_mode", false);
        //for test only
        if (BuildConfig.DEBUG) {
          if (false && private_key.isEmpty()) {
            private_key = ""; //debug private key goes here
            Toast.makeText(getBaseContext(), "test mode key " + private_key, Toast.LENGTH_SHORT).show();
          }
        }
        if (acct_addr.isEmpty() && private_key.isEmpty()) {
          show_welcome_dialog();
        } else if (acct_addr.isEmpty() && !private_key.isEmpty()) {
          if (private_key.length() != 64) {
            Util.show_err(getBaseContext(), "invalid private key; length is " + private_key.length(), 5);
            private_key = "";
          } else {
            BigInteger pk = new BigInteger(private_key, 16);
            ECKey ec_key = ECKey.fromPrivate(pk);
            acct_addr = "0x" + Hex.toHexString(ec_key.getAddress());
            if (acct_addr.isEmpty() || acct_addr.length() != 42) {
              Util.show_err(getBaseContext(), "invalid account address from key; length is " + acct_addr.length(), 5);
              private_key = "";
              acct_addr = "";
            } else {
              refresh_mode = true;
              last_nonce = 0;
              balance = 0;
              SharedPreferences.Editor preferences_editor = preferences.edit();
              preferences_editor.putString("acct_addr", acct_addr);
              preferences_editor.putLong("last_nonce", last_nonce);
              preferences_editor.commit();
              Toast.makeText(getBaseContext(), "new acct sucessfully imported: " + acct_addr, Toast.LENGTH_LONG).show();
            }
          }
        }
        if (acct_addr.isEmpty()) {
          last_nonce = 0;
          balance = 0;
        }
        dsp_balance();
        dsp_acct_addr();
        if (refresh_mode)
          schedule_refresh();
      }

    public void do_receive(View view) {
      if (acct_addr.isEmpty()) {
        String msg = getResources().getString(R.string.no_acct);
        Util.show_err(getBaseContext(), msg, 5);
        return;
      }
      Intent intent = new Intent(this, ReceiveActivity.class);
      startActivity(intent);
    }


    public void do_payments(View view) {
      if (acct_addr.isEmpty()) {
        String msg = getResources().getString(R.string.no_acct);
        Util.show_err(getBaseContext(), msg, 5);
        return;
      }
      Intent intent = new Intent(this, HistoryActivity.class);
      intent.putExtra("SHOW_RECEIVED", false);
      intent.putExtra("SHOW_SENT", true);
      startActivity(intent);
    }

    public void do_received(View view) {
      if (acct_addr.isEmpty()) {
        String msg = getResources().getString(R.string.no_acct);
        Util.show_err(getBaseContext(), msg, 5);
        return;
      }
      Intent intent = new Intent(this, HistoryActivity.class);
      intent.putExtra("SHOW_RECEIVED", true);
      intent.putExtra("SHOW_SENT", false);
      startActivity(intent);
    }


    public void schedule_refresh() {
      new CountDownTimer(10000, 5000) {
        public void onTick(long millisUntilFinished) {
          //mTextField.setText("seconds remaining: " + millisUntilFinished / 1000);
        }
        public void onFinish() {
          do_refresh(null);
        }
      }.start();
    }

    public void do_refresh(View view) {
      if (acct_addr.isEmpty()) {
        String msg = getResources().getString(R.string.no_acct);
        Util.show_err(getBaseContext(), msg, 5);
        return;
      }
      ImageButton refresh_button = (ImageButton) findViewById(R.id.refresh_button);
      refresh_button.setBackgroundColor(Color.GRAY);
      long now_sec = System.currentTimeMillis() / 1000;
      if (view != null || nonce < last_nonce) {
        if (now_sec - last_refresh_sec < 5) {
          Toast.makeText(context, "continuing refresh.... (" + nonce + "/" + last_nonce + ")", Toast.LENGTH_LONG).show();
          schedule_refresh();
        } else {
          last_refresh_sec = now_sec;
          (toast = Toast.makeText(context, "refreshing account status (nonce)...", Toast.LENGTH_SHORT)).show();
          String nonce_URL = "https://etherchain.org/api/account/" + acct_addr + "/nonce";
          String parms[] = new String[2];
          parms[0] = nonce_URL;
          parms[1] = "nonce-refresh";
          new HTTP_Query_Task(this, context).execute(parms);
        }
      } else if (last_balance_sec < last_nonce_sec) {
        last_refresh_sec = now_sec;
    	if (toast != null)
	      toast.cancel();
        (toast = Toast.makeText(context, "refreshing account status (balance)...", Toast.LENGTH_SHORT)).show();
        String balance_parms[] = new String[2];
        balance_parms[0] = "https://etherchain.org/api/account/" + acct_addr;
        balance_parms[1] = "balance-refresh";
        new HTTP_Query_Task(this, context).execute(balance_parms);
      } else if (last_price_sec < last_nonce_sec) {
        last_refresh_sec = now_sec;
	    if (toast != null)
	      toast.cancel();
        (toast = Toast.makeText(context, "refreshing account status (price)...", Toast.LENGTH_SHORT)).show();
        String price_parms[] = new String[2];
        price_parms[0] = "https://etherchain.org/api/basic_stats";
        price_parms[1] = "price-refresh";
        new HTTP_Query_Task(this, context).execute(price_parms);
      } else {
        refresh_button.setBackgroundColor(Color.TRANSPARENT);
        if (refresh_mode) {
          refresh_mode = false;
          dsp_balance();
        }
	    if (toast != null)
	      toast.cancel();
        Toast.makeText(context, "account status is up-to-date", Toast.LENGTH_SHORT).show();
      }
    }


    public void do_pay(View view) {
      long now_sec = System.currentTimeMillis() / 1000;
      if (acct_addr.isEmpty()) {
        String msg = getResources().getString(R.string.no_acct);
        Util.show_err(getBaseContext(), msg, 5);
        return;
      }
      if (nonce < last_nonce) {
        if (now_sec - last_pay_sec < 75) {
          String msg = getResources().getString(R.string.min_sec_between_pays);
          Util.show_err(getBaseContext(), msg, 10);
          return;
        } else {
          (toast = Toast.makeText(context, "refreshing account status (nonce)...", Toast.LENGTH_SHORT)).show();
          String nonce_URL = "https://etherchain.org/api/account/" + acct_addr + "/nonce";
          String parms[] = new String[2];
          parms[0] = nonce_URL;
          parms[1] = "nonce-pay";
          new HTTP_Query_Task(this, context).execute(parms);
        }
      }
      if (last_balance_sec < last_nonce_sec || (balance == 0 && now_sec - last_balance_sec < 10)) {
    	if (toast != null)
          toast.cancel();
        (toast = Toast.makeText(context, "refreshing account status (balance)...", Toast.LENGTH_SHORT)).show();
        String balance_parms[] = new String[2];
        balance_parms[0] = "https://etherchain.org/api/account/" + acct_addr;
        balance_parms[1] = "balance-pay";
        new HTTP_Query_Task(this, context).execute(balance_parms);
        return;
      }
      if (last_price_sec < last_nonce_sec || price == 0) {
        if (toast != null)
          toast.cancel();
        (toast = Toast.makeText(context, "refreshing account status (price)...", Toast.LENGTH_SHORT)).show();
        String price_parms[] = new String[2];
        price_parms[0] = "https://etherchain.org/api/basic_stats";
        price_parms[1] = "price-pay";
        new HTTP_Query_Task(this, context).execute(price_parms);
        return;
      }
      if (now_sec - last_price_sec < 5 && toast != null) {
        toast.cancel();
        (toast = Toast.makeText(context, "account status is up-to-date", Toast.LENGTH_SHORT)).show();
      }
      do_pay_guts();
    }

    private void show_welcome_dialog() {
      AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(context);
      alertDialogBuilder.setTitle("EtherPay");
      alertDialogBuilder.setMessage(getResources().getString(R.string.welcome));
      alertDialogBuilder.setCancelable(false);
      alertDialogBuilder.setNeutralButton("OK",
              new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog,int id) {
                  dialog.cancel();
                }
              });
      AlertDialog alertDialog = alertDialogBuilder.create();
      alertDialog.show();
    }


    private void show_about_dialog() {
      AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(context);
      alertDialogBuilder.setTitle("About EtherPay");
      alertDialogBuilder.setMessage(getResources().getString(R.string.about));
      alertDialogBuilder.setCancelable(false);
      alertDialogBuilder.setNeutralButton("OK",
              new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog,int id) {
                  dialog.cancel();
                }
              });
      AlertDialog alertDialog = alertDialogBuilder.create();
      alertDialog.show();
    }


    private boolean ask_delete_old_key() {
      if (private_key.isEmpty())
        return(true);
      AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(context);
      alertDialogBuilder.setTitle("Delete current account?");
      alertDialogBuilder.setMessage(getResources().getString(R.string.ask_delete_old_key));
      alertDialogBuilder.setCancelable(false);
      alertDialogBuilder.setPositiveButton("Continue",
              new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog,int id) {
                  delete_old_key_import_new();
                }
              });
      alertDialogBuilder.setNegativeButton("Cancel",new DialogInterface.OnClickListener() {
        public void onClick(DialogInterface dialog,int id) {
          dialog.cancel();
        }
      });
      AlertDialog alertDialog = alertDialogBuilder.create();
      alertDialog.show();
      return(false);
    }


    private void delete_old_key_import_new() {
      SharedPreferences.Editor preferences_editor = preferences.edit();
      preferences_editor.putString("acct_addr", "");
      preferences_editor.putString("key", "");
      preferences_editor.apply();
      String scan_key_prompt = getResources().getString(R.string.scan_key_prompt);
      Intent intent = new Intent(this, ScanActivity.class);
      intent.putExtra("SCAN_PROMPT", scan_key_prompt);
      intent.putExtra("TARGET_ACTIVITY", "MainActivity");
      startActivity(intent);
    }


    private void do_pay_guts() {
      String scan_addr_prompt = getResources().getString(R.string.scan_addr_prompt);
      Intent intent = new Intent(this, ScanActivity.class);
      intent.putExtra("SCAN_PROMPT", scan_addr_prompt);
      intent.putExtra("TARGET_ACTIVITY", "SendActivity");
      startActivity(intent);
    }


    public void handle_http_rsp(String callback, String rsp) {
      int next_idx = callback.indexOf("-");
      String next_callback = (next_idx >= 0) ? callback.substring(next_idx + 1) : "";
      if (callback.startsWith("balance")) {
        set_balance(rsp);
      } else if (callback.startsWith("price")) {
        set_price(rsp);
      } else if (callback.startsWith("nonce")) {
        set_nonce(rsp);
      }
      //
      if (next_callback.startsWith("refresh")) {
        do_refresh(null);
      } else if (next_callback.equals("pay")) {
        do_pay(null);
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
      nonce = 0;
      if (nonce_rsp.contains("accountNonce")) {
        nonce_rsp = nonce_rsp.replaceAll("\"", "");
        int field_idx = nonce_rsp.indexOf("accountNonce") + "accountNonce".length();
        int beg_idx = nonce_rsp.indexOf(':', field_idx) + 1;
        int end_idx = nonce_rsp.indexOf('}', beg_idx);
        String nonce_str = nonce_rsp.substring(beg_idx, end_idx).trim();
        if (!nonce_str.equals("null")) {
          nonce = Long.valueOf(nonce_str);
          last_nonce_sec = System.currentTimeMillis() / 1000;
        }
      } else {
        Util.show_err(getBaseContext(), "error retreiving nonce!", 3);
        Util.show_err(getBaseContext(), nonce_rsp, 10);
      }
    }



    private void set_balance(String rsp) {
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
      if (rsp.contains("balance")) {
        int field_idx = rsp.indexOf("balance") + "balance".length();
        int beg_idx = rsp.indexOf(':', field_idx) + 1;
        int end_idx = rsp.indexOf(',', beg_idx);
        String balance_str = rsp.substring(beg_idx, end_idx).trim();
        if (!balance_str.equals("null")) {
          balance = Float.valueOf(balance_str) / WEI_PER_ETH;
          last_balance_sec = System.currentTimeMillis() / 1000;
        }
      } else {
        Util.show_err(getBaseContext(), "error retreiving balance!", 3);
        Util.show_err(getBaseContext(), rsp, 10);
      }
      //Toast.makeText(context, "balance = " + balance, Toast.LENGTH_LONG).show();
      dsp_balance();
    }

    private void set_price(String rsp) {
      //typical response is:
      //{
      // .....
      //    "price": {
      //        "usd": 9.71,
      //        "btc": 0.01291
      //    },
      //    "stats": {
      //        "blockTime": 14.0749,
      //        "difficulty": 71472687483186.2,
      //        "hashRate": 5270984772944.684,
      //        "uncle_rate": 0.0686
      //    }
      //}
      if (rsp.contains("price")) {
        int price_field_idx = rsp.indexOf("price") + "price".length();
        rsp = rsp.substring(price_field_idx);
        if (rsp.contains("usd")) {
          int field_idx = rsp.indexOf("usd") + "usd".length();
          int beg_idx = rsp.indexOf(':', field_idx) + 1;
          int end_idx = rsp.indexOf(',', beg_idx);
          String price_str = rsp.substring(beg_idx, end_idx).trim();
          if (!price_str.equals("null")) {
            price = Float.valueOf(price_str);
            last_price_sec = System.currentTimeMillis() / 1000;
          }
        }
      }
      dsp_balance();
    }


    private void dsp_balance() {
      TextView balance_view = (TextView) findViewById(R.id.balance);
      String balance_str = String.format("%7.05f", balance) + " ETH";
      balance_view.setText(String.valueOf(balance_str));
      TextView usd_balance_view = (TextView) findViewById(R.id.usd_balance);
      float usd_balance = balance * price;
      String usd_balance_str = String.format("%2.02f", usd_balance) + " USD";
      usd_balance_view.setText(String.valueOf(usd_balance_str));
      if (refresh_mode) {
        Animation anim = new AlphaAnimation(0.0f, 1.0f);
        anim.setDuration(150);
        anim.setStartOffset(20);
        anim.setRepeatMode(Animation.REVERSE);
        anim.setRepeatCount(Animation.INFINITE);
        balance_view.startAnimation(anim);
      } else {
        balance_view.clearAnimation();
      }
    }

    private void dsp_acct_addr() {
      TextView acct_view = (TextView) findViewById(R.id.wallet_id);
      //String acct_str = acct_addr.substring(0, 20) + " ...";
      acct_view.setText(String.valueOf(acct_addr));
    }

  }
