  package com.bringcommunications.etherpay;

/*
  overview of vars stored in preferences:

  private_key      => current account's private key
  acct_addr        => current account address
  refresh_mode     => mode is set after a payment completes, until we refresh balance

*/


  import android.content.DialogInterface;
  import android.content.Intent;
  import android.content.SharedPreferences;
  import android.graphics.Color;
  import android.os.Bundle;
  import android.os.CountDownTimer;
  import android.os.Handler;
  import android.os.Looper;
  import android.os.Message;
  import android.support.v7.app.AlertDialog;
  import android.support.v7.app.AppCompatActivity;
  import android.support.v7.widget.Toolbar;
  import android.view.Menu;
  import android.view.MenuInflater;
  import android.view.MenuItem;
  import android.view.View;
  import android.view.animation.AlphaAnimation;
  import android.view.animation.Animation;
  import android.view.animation.AnimationUtils;
  import android.widget.FrameLayout;
  import android.widget.ImageButton;
  import android.widget.ImageView;
  import android.widget.TextView;
  import android.widget.Toast;

  import org.ethereum.crypto.ECKey;
  import org.spongycastle.util.encoders.Hex;

  import java.math.BigInteger;


  public class MainActivity extends AppCompatActivity implements Payment_Processor_Client {

    private String acct_addr = "";
    private String private_key = "";
    private boolean refresh_mode = false;
    private boolean do_pay = false;
    private SharedPreferences preferences;
    private FrameLayout overlay_frame_layout;
    private MainActivity context;
    private Toast toast = null;
    private Handler message_handler = null;

    //these defines are for our handle_message fcn, which displays dialogs on behalf of other threads (payment_processor)
    private static final int HANDLE_BALANCE_CALLBACK = 1;
    private static final int HANDLE_INTERIM_CALLBACK = 2;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
      super.onCreate(savedInstanceState);
      overlay_frame_layout = new FrameLayout(getApplicationContext());
      setContentView(overlay_frame_layout);
      View activity_main_view = getLayoutInflater().inflate(R.layout.activity_main, overlay_frame_layout, false);
      setContentView(activity_main_view);
      Toolbar toolbar = (Toolbar)findViewById(R.id.toolbar);
      String app_name = getResources().getString(R.string.app_name);
      String subtitle = getResources().getString(R.string.main_subtitle);
      toolbar.setTitle(app_name);
      if (!subtitle.isEmpty())
	toolbar.setSubtitle(subtitle);
      toolbar.setBackgroundResource(R.color.color_toolbar);
      setSupportActionBar(toolbar);
      context = this;
      String app_uri = getResources().getString(R.string.app_uri);
      preferences = getSharedPreferences(app_uri, MODE_PRIVATE);
      message_handler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message message) {
          switch (message.what) {
            case HANDLE_BALANCE_CALLBACK: {
              boolean ok = (message.arg1 != 0);
              if (ok) {
                dsp_balance();
                ImageButton refresh_button = (ImageButton) findViewById(R.id.refresh_button);
                refresh_button.clearAnimation();
                TextView finney_balance_view = (TextView) findViewById(R.id.eth_balance);
                finney_balance_view.clearAnimation();
                refresh_mode = false;
                if (toast != null)
                  toast.cancel();
                Toast.makeText(context, "account status is up-to-date", Toast.LENGTH_SHORT).show();
              } else {
                AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(context);
                alertDialogBuilder.setTitle("Uh Oh: Internet Connectivity Problem");
                String msg = "Your Internet connection failed, and we were unable to retrieve your actual balance." +
                        "\n\nPlease check your Internet connection";
                alertDialogBuilder.setMessage(msg);
                alertDialogBuilder.setCancelable(false);
                alertDialogBuilder.setNeutralButton("OK",
                        new DialogInterface.OnClickListener() {
                          public void onClick(DialogInterface dialog, int id) {
                            dialog.cancel();
                            finish();
                          }
                        });
                AlertDialog alertDialog = alertDialogBuilder.create();
                alertDialog.show();
              }
              break;
            }
            //
            case HANDLE_INTERIM_CALLBACK: {
              String msg = (String) message.obj;
              if (toast != null)
                toast.cancel();
              (toast = Toast.makeText(context, msg, Toast.LENGTH_LONG)).show();
              break;
            }
          }
        }
      };
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
        case R.id.export_account:
          if (acct_addr.isEmpty()) {
            String msg = getResources().getString(R.string.no_acct);
            Util.show_err(getBaseContext(), msg, 5);
            return true;
          }
          Intent intent = new Intent(this, ReceiveActivity.class);
          intent.putExtra("SHOW_PRIVATE", true);
          startActivity(intent);
          return true;
        case R.id.welcome:
          show_welcome_dialog();
          return true;
        case R.id.help:
	      do_help(null);
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
        preferences_editor.putBoolean("refresh_mode", false);
        preferences_editor.commit();
    }

    public void onResume() {
        super.onResume();  // Always call the superclass method first
        private_key = preferences.getString("key", private_key);
        acct_addr = preferences.getString("acct_addr", "");
        refresh_mode = preferences.getBoolean("refresh_mode", false);
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
            if (acct_addr.length() != 42) {
              Util.show_err(getBaseContext(), "invalid account address from key; length is " + acct_addr.length(), 5);
              private_key = "";
              acct_addr = "";
            } else {
              refresh_mode = true;
              SharedPreferences.Editor preferences_editor = preferences.edit();
              preferences_editor.putString("acct_addr", acct_addr);
              preferences_editor.putLong("balance", 0);
              preferences_editor.putLong("verified_balance", 0);
              preferences_editor.putLong("balance_refresh_sec", 0);
              preferences_editor.putLong("last_tx_nonce", -1);
              preferences_editor.putLong("verified_nonce", -2);
              preferences_editor.putLong("nonce_refresh_sec", 0);
              preferences_editor.putLong("verified_balance_changed_sec", 0);
              preferences_editor.putBoolean("acct_has_no_txs", false);
              preferences_editor.commit();
              Toast.makeText(getBaseContext(), "new acct sucessfully imported: " + acct_addr, Toast.LENGTH_LONG).show();
            }
          }
        }
        long balance = preferences.getLong("balance", 0);
        long verified_balance = preferences.getLong("verified_balance", 0);
        if (balance != verified_balance)
          refresh_mode = true;
        dsp_balance();
        dsp_acct_addr();
        if (refresh_mode) {
          do_refresh(null);
        } else {
          //ensure that we are not displaying these animations from any previous refresh that was incomplete
          ImageButton refresh_button = (ImageButton) findViewById(R.id.refresh_button);
          refresh_button.clearAnimation();
          TextView eth_balance_view = (TextView) findViewById(R.id.eth_balance);
          eth_balance_view.clearAnimation();
        }
      }

    public void onStop() {
      if (toast != null)
        toast.cancel();
      //if we were waiting to update balance, no need to keep that request queued anymore
      Payment_Processor.cancel_messages(this);
      super.onStop();
    }

    public void do_help(View view) {
      android.support.v7.app.AlertDialog.Builder alertDialogBuilder = new android.support.v7.app.AlertDialog.Builder(context);
      alertDialogBuilder.setTitle(getResources().getString(R.string.wallet_help_title));
      alertDialogBuilder.setMessage(getResources().getString(R.string.wallet_help));
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

    public void do_receive(View view) {
      if (acct_addr.isEmpty()) {
        String msg = getResources().getString(R.string.no_acct);
        Util.show_err(getBaseContext(), msg, 5);
        return;
      }
      Intent intent = new Intent(this, ReceiveActivity.class);
      intent.putExtra("SHOW_PRIVATE", false);
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


    public void do_refresh(View view) {
      if (acct_addr.isEmpty()) {
        String msg = getResources().getString(R.string.no_acct);
        Util.show_err(getBaseContext(), msg, 5);
        return;
      }
      ImageButton refresh_button = (ImageButton) findViewById(R.id.refresh_button);
      refresh_button.startAnimation(AnimationUtils.loadAnimation(this, R.anim.rotate_forever));
      if (!Payment_Processor.has_message(this))
        Payment_Processor.refresh_balance(this, context);
    }


    public void do_pay(View view) {
      long now_sec = System.currentTimeMillis() / 1000;
      if (acct_addr.isEmpty()) {
        String msg = getResources().getString(R.string.no_acct);
        Util.show_err(getBaseContext(), msg, 5);
        return;
      }
      long last_tx_nonce = preferences.getLong("last_tx_nonce", -1);
      long verified_nonce = preferences.getLong("verified_nonce", -2);
      long price_refresh_sec = preferences.getLong("price_refresh_sec", 0);
      long balance = preferences.getLong("balance", 0);
      long verified_balance = preferences.getLong("verified_balance", 0);
      long balance_refresh_sec = preferences.getLong("balance_refresh_sec", 0);
      if ((verified_nonce < last_tx_nonce)                                                     ||
          (now_sec - price_refresh_sec > 5 * 60)                                               ||
          (balance == 0 || balance != verified_balance || now_sec - balance_refresh_sec > 120)) {
          do_pay = true;
          Payment_Processor.refresh_balance(this, context);
      }
      do_pay = false;
      do_pay_guts();
    }

    private void show_welcome_dialog() {
      AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(context);
      String app_name = getResources().getString(R.string.app_name);    
      alertDialogBuilder.setTitle(app_name);
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
      String app_name = getResources().getString(R.string.app_name);    
      alertDialogBuilder.setTitle("About " + app_name);
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


    private void dsp_balance() {
      long wei_balance = preferences.getLong("balance", 0);
      float eth_balance = (float)wei_balance / Util.WEI_PER_ETH;      
      TextView eth_balance_view = (TextView) findViewById(R.id.eth_balance);
      String eth_balance_str = String.format("%7.05f", eth_balance) + " ETH";
      eth_balance_view.setText(String.valueOf(eth_balance_str));
      TextView usd_balance_view = (TextView) findViewById(R.id.usd_balance);
      float usd_price = preferences.getFloat("usd_price", 0);
      float usd_balance = eth_balance * usd_price;
      String usd_balance_str = String.format("%2.02f", usd_balance) + " USD";
      usd_balance_view.setText(String.valueOf(usd_balance_str));
      if (refresh_mode) {
        Animation anim = new AlphaAnimation(0.0f, 1.0f);
        anim.setDuration(150);
        anim.setStartOffset(20);
        anim.setRepeatMode(Animation.REVERSE);
        anim.setRepeatCount(Animation.INFINITE);
        eth_balance_view.startAnimation(anim);
      } else {
        eth_balance_view.clearAnimation();
      }
    }

    private void dsp_acct_addr() {
      TextView acct_view = (TextView) findViewById(R.id.wallet_id);
      //String acct_str = acct_addr.substring(0, 20) + " ...";
      acct_view.setText(String.valueOf(acct_addr));
    }


    //this is the callback from Payment_Processor
    public boolean payment_result(boolean ok, String txid, long size_wei, String client_data, String error) {
      System.out.println("MainActivity::payment_result: Hey! we should never be here!");
      return true;
    }
    public void interim_payment_result(long size_wei, String client_data, String msg) {
      System.out.println("Hey! we should never be here!");
    }
    public void balance_result(boolean ok, long balance, String error) {
      System.out.println("ShareActivity::balance_result: payment processor completed. balance = " + balance);
      if (do_pay) {
        do_pay_guts();
      } else {
        Message message = message_handler.obtainMessage(HANDLE_BALANCE_CALLBACK, ok ? 1 : 0, 0);
        message.sendToTarget();
      }
    }
    public void interim_balance_result(String msg) {
      Message message = message_handler.obtainMessage(HANDLE_INTERIM_CALLBACK, 0, 0, msg);
      message.sendToTarget();
    }

  }
