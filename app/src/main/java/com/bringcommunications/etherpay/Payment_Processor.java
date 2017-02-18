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

import static android.content.Context.MODE_PRIVATE;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Message;
import android.view.View;
import android.widget.ImageButton;

import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.DecoderException;

import static org.ethereum.util.ByteUtil.longToBytes;
import static org.ethereum.util.ByteUtil.longToBytesNoLeadZeroes;
import org.ethereum.core.Transaction;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;

/**
 * Created by dbrosen on 1/2/17.
 */

class Payment_Processor implements Runnable, HTTP_Query_Client {

    private Hex hex;
    private SharedPreferences preferences;
    private static Payment_Processor payment_processor = null;
    //after verified nonce has not changed for this long, adopt the verified nonce as our last_tx_nonce, even if we
    //thought we had performed other transactions (not reflected in the verified nonce)
    //20170130: increased the NONCE_FORCED_ADOPTION_SEC from 180 to 210, cuz now we send transactions via the backend server,
    //which might delay an extra couple of seconds while waiting for startGame.
    private static long NONCE_FORCED_ADOPTION_SEC = 210;
    private static long BALANCE_FORCED_ADOPTION_SEC = 60;
    private static long LONG_ETHER_TRANSACTION_TIME_SEC = 60;
    private static long BALANCE_ESTIMATE_SLOP = 3 * Util.WEI_PER_FINNEY;

    private Payment_Message current_send_message = null;
    private Payment_Message current_balance_message = null;
    private List<Payment_Message> send_message_list;
    private List<Payment_Message> balance_message_list;
    private Object monitor;
    private HashMap<Payment_Processor_Client, Integer> client_map;
    private long mem_avail_kb;
    private long gas_price_history[];
    private enum Message_Type { SEND, BALANCE };
    private long encrypt_sec_history = -1;

    //this class must be declared static:
    // A nested class is a member of its enclosing class. Non-static nested classes (inner classes) have access to other members of the
    // enclosing class, even if they are declared private. Static nested classes do not have access to other members of the enclosing class.
    // client_data is a data field for the benefit of the payment_processor client, to use as he wishes. we pass it back in the payment_result
    // callback.
    private static class Payment_Message {
        Payment_Processor_Client client;
        Context context;
        String to_addr;
        long size_wei;
        long gas_limit;
        long nonce;
        byte data[];
        String client_data;
        int process_count;
        long wait_until_sec;
        boolean is_suspended;
        boolean want_raw_tx;

        public Payment_Message(Payment_Processor_Client client, Context context, String client_data, String to_addr, long size_wei, long gas_limit, byte data[], boolean want_raw_tx) {
            this.client = client;
            this.context = context;
            this.client_data = client_data;
            this.to_addr = to_addr;
            this.size_wei = size_wei;
            this.gas_limit = gas_limit;
            this.data = data;
            this.want_raw_tx = want_raw_tx;
            this.nonce = 0;
            this.process_count = 0;
            this.wait_until_sec = 0;
            this.is_suspended = true;
        }
    };


    /* ------------------------------------------------------------------------------------------------------------------------
    these are the public entry points
    ------------------------------------------------------------------------------------------------------------------------ */
    public static void send(Payment_Processor_Client client, Context context, String client_data, String to_addr, long size_wei, long gas_limit, byte data[], boolean want_raw_tx) {
        if (payment_processor == null) {
            payment_processor = new Payment_Processor(context);
            Thread thread = new Thread(payment_processor);
            thread.start();
        }
        Payment_Message message = new Payment_Message(client, context, client_data, to_addr, size_wei, gas_limit, data, want_raw_tx);
        payment_processor.queue_message(message, Message_Type.SEND);
    }

    public static void send(Payment_Processor_Client client, Context context, String to_addr, long size_wei) {
        send(client, context, "", to_addr, size_wei, Util.DEFAULT_GAS_LIMIT, null, false);
    }

    public static void refresh_balance(Payment_Processor_Client client, Context context) {
        if (payment_processor == null) {
            payment_processor = new Payment_Processor(context);
            Thread thread = new Thread(payment_processor);
            thread.start();
        }
        Payment_Message message = new Payment_Message(client, context, "", "", 0, 0, null, false);
        payment_processor.queue_message(message, Message_Type.BALANCE);
    }
    public static boolean has_message(Payment_Processor_Client client) {
        if (payment_processor != null) {
            Integer count = payment_processor.client_map.get(client);
            if (count != null)
                return (count.intValue() != 0);
        }
        return false;
    }
    public static void cancel_messages(Payment_Processor_Client client) {
        if (payment_processor != null)
            payment_processor.cancel_client_messages(client);
    }



    Payment_Processor(Context context) {
        String app_uri = context.getResources().getString(R.string.app_uri);
        preferences = context.getSharedPreferences(app_uri, MODE_PRIVATE);
        send_message_list = new LinkedList<Payment_Message>();
        balance_message_list = new LinkedList<Payment_Message>();
        client_map = new HashMap<Payment_Processor_Client, Integer>();
        monitor = new Object();
        hex = new Hex();
        gas_price_history = new long[5];
        for (int i = 0; i < gas_price_history.length; ++i)
            gas_price_history[i] = Long.MAX_VALUE;
        mem_avail_kb = Util.get_avail_memory_kb(context);
    }

    private void queue_message(Payment_Message message, Message_Type message_type) {
        synchronized (monitor) {
            Integer count = client_map.get(message.client);
            if (count == null)
                count = new Integer(0);
            count = new Integer(count.intValue() + 1);
            client_map.put(message.client, count);
            if (message_type == Message_Type.SEND)
                send_message_list.add(message);
            else
                balance_message_list.add(message);
            monitor.notify();
        }
    }

    private void dispose_current_message(String callback, boolean ok, String txid, long balance, String err_msg, boolean flush_all) {
        Payment_Message payment_message = callback.equals("refresh") ? current_balance_message : current_send_message;
        Payment_Processor_Client client = payment_message.client;
        long size_wei = payment_message.size_wei;
        String client_data = payment_message.client_data;
        boolean want_raw_tx = payment_message.want_raw_tx;
        long tx_nonce = payment_message.nonce;
        synchronized (monitor) {
            if (flush_all) {
                client_map.clear();
                send_message_list.clear();
                balance_message_list.clear();
            } else {
                Integer count = client_map.get(payment_message.client);
                if (count == null)
                    count = new Integer(0);
                count = new Integer(Math.max(count.intValue() - 1, 0));
                client_map.put(client, count);
            }
            if (callback.equals("refresh"))
                current_balance_message = null;
            else
                current_send_message = null;
            monitor.notify();
        }
        if (callback.equals("refresh")) {
            client.balance_result(ok, balance, err_msg);
        } else {
            boolean payment_ok = client.payment_result(ok, txid, size_wei, client_data, err_msg);
            //if the payment processing was successful, but the client wanted the raw tx, to broadcast the tx himself, then only
            //update preferences after the broadcast completes successfully
            if (ok && payment_ok && want_raw_tx) {
                long now_sec = System.currentTimeMillis() / 1000;
                SharedPreferences.Editor preferences_editor = preferences.edit();
                preferences_editor.putLong("last_tx_nonce", tx_nonce);
                preferences_editor.putLong("last_pay_sec", now_sec);
                long oldest_unverified_tx = preferences.getLong("oldest_unverified_tx", 0);
                long verified_nonce_changed_sec = preferences.getLong("verified_nonce_changed_sec", 0);
                //oldest-verified-tx isn't really the oldest-*verified*-tx; it's actually the oldest tx since the verified nonce last changed.
                //it's useful to indicate how long the nonce has been stuck.
                if (oldest_unverified_tx < verified_nonce_changed_sec)
                    preferences_editor.putLong("oldest_unverified_tx", now_sec);
                preferences_editor.putLong("balance", balance);
                preferences_editor.putBoolean("refresh_mode", true);
                preferences_editor.apply();
            }
        }
    }

    private void cancel_client_messages(Payment_Processor_Client client) {
        synchronized (monitor) {
            Integer count = client_map.get(client);
            int no_entries = (count == null) ? 0 : count.intValue();
            ListIterator<Payment_Message> sit = send_message_list.listIterator();
            while (sit.hasNext() &&  no_entries > 0) {
                if (sit.next().client.equals(client)) {
                    --no_entries;
                    sit.remove();
                }
            }
            ListIterator<Payment_Message> bit = balance_message_list.listIterator();
            while (bit.hasNext() &&  no_entries > 0) {
                if (bit.next().client.equals(client)) {
                    --no_entries;
                    bit.remove();
                }
            }
            count = new Integer(no_entries);
            client_map.put(client, count);
        }
    }


    //if any current message is already activly being processed (is not suspended), then we wait the max time (until we are notified)
    //
    public void run() {
        while (true) {
            long wait_sec;
            do {
                wait_sec = Long.MAX_VALUE;
                synchronized (monitor) {
                    long now_sec = System.currentTimeMillis() / 1000;
                    if ((current_send_message    != null && !current_send_message.is_suspended   ) ||
                        (current_balance_message != null && !current_balance_message.is_suspended) ) {
                            System.out.println("payment_processor::run - have active message"); //we have an active process... wait max time
                    } else {
                        if (wait_sec > 0 && current_send_message != null) {
                            if ((wait_sec = Math.min(wait_sec, Math.max(0, current_send_message.wait_until_sec - now_sec))) == 0) {
                                System.out.println("payment_processor::run - suspended send message is ready");
                                current_send_message.wait_until_sec = 0;
                            } else
                                System.out.println("payment_processor::run - have suspended send message (" + wait_sec + " more secs)");
                        }
                        if (wait_sec > 0 && current_balance_message != null) {
                            if ((wait_sec = Math.min(wait_sec, Math.max(0, current_balance_message.wait_until_sec - now_sec))) == 0) {
                                System.out.println("payment_processor::run - suspended balance message is ready");
                                current_balance_message.wait_until_sec = 0;
                            } else
                                System.out.println("payment_processor::run - have suspended balance message (" + wait_sec + " more secs)");
                        }
                        if (wait_sec > 0 && current_send_message == null && !send_message_list.isEmpty()) {
                            System.out.println("payment_processor::run - got new send message");
                            current_send_message = send_message_list.remove(0);
                            wait_sec = 0;
                        }
                        if (wait_sec > 0 && current_balance_message == null && !balance_message_list.isEmpty()) {
                            System.out.println("payment_processor::run - got new balance message");
                            current_balance_message = balance_message_list.remove(0);
                            wait_sec = 0;
                        }
                    }
                    if (wait_sec > 0) {
                        wait_sec = Math.min(wait_sec, 15);
                        System.out.println("Payment_Processor waiting " + wait_sec + " seconds");
                        try {
                            monitor.wait(wait_sec * 1000);
                        } catch (InterruptedException e) {
                            System.out.println("Payment_Processor wait interrupted " + e.toString());
                        }
                    }
                }
            } while (wait_sec > 0);
            if (current_send_message != null && current_send_message.wait_until_sec == 0) {
                System.out.println("Payment_Processor process send message");
                current_send_message.is_suspended = false;
                send_guts();
            } else if (current_balance_message != null && current_balance_message.wait_until_sec == 0) {
                System.out.println("Payment_Processor process balance message");
                current_balance_message.is_suspended = false;
                refresh_balance_guts();
            }
        }
    }


    /* ------------------------------------------------------------------------------------------------------------------------
    guts of transaction processing below
    ------------------------------------------------------------------------------------------------------------------------ */
    public void refresh_balance_guts() {
        long now_sec = System.currentTimeMillis() / 1000;
        ++current_balance_message.process_count;
        long last_tx_nonce = preferences.getLong("last_tx_nonce", -1);
        long verified_nonce = preferences.getLong("verified_nonce", -2);
        long nonce_refresh_sec = preferences.getLong("nonce_refresh_sec", now_sec);
        boolean tx_err_occurred = preferences.getBoolean("tx_err_occurred", false);
        System.out.println("Payment_Processor::refresh_balance_guts -- last tx nonce: " + last_tx_nonce + "; verified nonce: " + verified_nonce);
        if (verified_nonce != last_tx_nonce) {
            if (now_sec - nonce_refresh_sec < 7 && current_balance_message.process_count > 1)
                current_balance_message.client.interim_balance_result("continuing refresh.... (" + verified_nonce + "/" + last_tx_nonce + ")");
            else
                current_balance_message.client.interim_balance_result("refreshing account status (nonce)...");
            //it's less heavy-handed to retrieve the nonce from the nonce-api call. but sometimes that call provides the wrong nonce (especially
            //after a rapit-fire series of tx's. so if we've tried twice and we still don't have the correct nonce, then retrieve the nonce from
            //the transactions history.
            String acct_addr = preferences.getString("acct_addr", "");
            String parms[] = new String[2];
            if (tx_err_occurred || current_balance_message.process_count > 2) {
                int offset = 0;
                System.out.println("Payment_Processor::refresh_balance_guts -- refresh nonce from transaction history");
                String transactions_URL = "https://etherchain.org/api/account/" + acct_addr + "/tx/" + offset;
                parms[0] = transactions_URL;
                parms[1] = "transactions-refresh";
            } else {
                System.out.println("Payment_Processor::refresh_balance_guts -- refresh nonce");
                String nonce_URL = "https://etherchain.org/api/account/" + acct_addr + "/nonce";
                parms[0] = nonce_URL;
                parms[1] = "nonce-refresh";
            }
            new HTTP_Query_Task(this, current_balance_message.context).execute(parms);
            return;
        }

        //
        long price_refresh_sec = preferences.getLong("price_refresh_sec", 0);
        System.out.println("now = " + now_sec + "; price_refresh_sec = " + price_refresh_sec);
        if (now_sec - price_refresh_sec > 5 * 60) {
            current_balance_message.client.interim_balance_result("refreshing account status (price)...");
            String price_parms[] = new String[2];
            price_parms[0] = "https://etherchain.org/api/basic_stats";
            price_parms[1] = "price-refresh";
            new HTTP_Query_Task(this, current_balance_message.context).execute(price_parms);
            return;
        }
        //float usd_price = preferences.getFloat("usd_price", 0);
        //
        long balance = preferences.getLong("balance", 0);
        long verified_balance = preferences.getLong("verified_balance", 0);
        long balance_refresh_sec = preferences.getLong("balance_refresh_sec", 0);
        if (now_sec - balance_refresh_sec > 30 || balance_refresh_sec < nonce_refresh_sec || balance != verified_balance) {
            if (now_sec - balance_refresh_sec < 7 && current_balance_message.process_count > 1)
                current_balance_message.client.interim_balance_result("continuing refresh.... (balance)");
            else
                current_balance_message.client.interim_balance_result("refreshing account status (balance)...");
            String acct_addr = preferences.getString("acct_addr", "");
            String balance_parms[] = new String[2];
            balance_parms[0] = "https://etherchain.org/api/account/" + acct_addr;
            balance_parms[1] = "balance-refresh";
            new HTTP_Query_Task(this, current_balance_message.context).execute(balance_parms);
            return;
        }
        dispose_current_message("refresh", true, "", verified_balance, "", false);
    }



    //send msg = { to_addr, size_wei: x, gas_limit_wei: x, data: x }
    public void send_guts() {
        ++current_send_message.process_count;
        long now_sec = System.currentTimeMillis() / 1000;
        System.out.println("Payment_Processor::send_guts -- process count = " + current_send_message.process_count + ", now_sec = " + now_sec);
        //ensure that nonce read from blockchain (in send_msg) eq. last_nonce that we used
        //note that first payment nonce is 0; so if you've never made a payment then we have a convention that our "last-used-nonce" is -1.
        //lt -1 means we've never even tried to update
        long last_tx_nonce = preferences.getLong("last_tx_nonce", -1);
        long verified_nonce = preferences.getLong("verified_nonce", -2);
        boolean acct_has_no_txs = preferences.getBoolean("acct_has_no_txs", false);
        System.out.println("send_guts: last tx nonce: " + last_tx_nonce + "; verified nonce: " + verified_nonce);
        String acct_addr = preferences.getString("acct_addr", "");
        //
        //figure nonce. if verified nonce agrees with last tx nonce, then go right ahead. if it is less, then we might enable "rapid-fire"
        //payments; if verified nonce is gt. last tx nonce, then we will adopt varified nonce... but that logic is in handle_http_rsp
        current_send_message.nonce = -1;
        long nonce_refresh_sec = preferences.getLong("nonce_refresh_sec", 0);
        long nonce_settled_sec = preferences.getLong("nonce_settled_sec", 0);
        boolean tx_err_occurred = preferences.getBoolean("tx_err_occurred", false);
        if (tx_err_occurred) {
            System.out.println("send_guts: tx_err_occurred is set; no optimizatinos");
        } else if (verified_nonce == last_tx_nonce || acct_has_no_txs) {
            current_send_message.nonce = verified_nonce + 1;
            if (acct_has_no_txs) {
                //here's a little optimzation for the first couple of tx's (after this one). at this time we know that the nonce is correct, cuz the acct
                //has never been used. so we can enable rapid-fire tx's on the subsequent tx's by faking nonce-settled, and a recent nonce-refresh.
                SharedPreferences.Editor preferences_editor = preferences.edit();
                nonce_refresh_sec = nonce_settled_sec = now_sec;
                preferences_editor.putLong("nonce_refresh_sec", nonce_refresh_sec);
                preferences_editor.putLong("nonce_settled_sec", nonce_settled_sec);
                preferences_editor.apply();
            }
        } else if (verified_nonce < last_tx_nonce) {
            long last_pay_sec = preferences.getLong("last_pay_sec", 0);
            boolean enable_rapid_tx = current_send_message.context.getResources().getBoolean(R.bool.enable_rapid_tx);
            //if a) we've made payment(s) since nonce was last settled
            //   b) those payments were within the typical ether transaction time
            //   c) we tried updating nonce recently
            //then go ahead and just use our last-tx-nonce.... otherwise we better wait for the verified nonce
            if (last_pay_sec >= nonce_settled_sec && now_sec - last_pay_sec <= LONG_ETHER_TRANSACTION_TIME_SEC && now_sec - nonce_refresh_sec < LONG_ETHER_TRANSACTION_TIME_SEC && enable_rapid_tx) {
                System.out.println("send_guts: rapid-fire tx! last_pay_sec = " + last_pay_sec + ", nonce_settled_sec = " + nonce_settled_sec + ", nonce_refresh_sec = " + nonce_refresh_sec);
                current_send_message.nonce = last_tx_nonce + 1;
                SharedPreferences.Editor preferences_editor = preferences.edit();
                preferences_editor.putLong("rapid_fire_sec", now_sec);
                preferences_editor.apply();
            } else {
                System.out.println("send_guts: not performing rapid-fire tx!");
                System.out.println("last_pay_sec = " + last_pay_sec + ", nonce_settled_sec = " + nonce_settled_sec + ", nonce_refresh_sec = " + nonce_refresh_sec);
            }
        }
        if (current_send_message.nonce < 0) {
            String parms[] = new String[2];
            //it's less heavy-handed to retrieve the nonce from the nonce-api call. but sometimes that call provides the wrong nonce (especially
            //after a rapit-fire series of tx's. so if we've tried twice and we still don't have the correct nonce, then retrieve the nonce from
            //the transactions history.
            long rapid_fire_sec = preferences.getLong("rapid_fire_sec", 0);
            if (tx_err_occurred || rapid_fire_sec > nonce_settled_sec || current_send_message.process_count > 2 || (now_sec & 7) == 0) {
                int offset = 0;
                System.out.println("Payment_Processor: refresh nonce from transaction history");
                String transactions_URL = "https://etherchain.org/api/account/" + acct_addr + "/tx/" + offset;
                parms[0] = transactions_URL;
                parms[1] = "transactions-send";
            } else {
                System.out.println("Payment_Processor: refresh nonce");
                String nonce_URL = "https://etherchain.org/api/account/" + acct_addr + "/nonce";
                parms[0] = nonce_URL;
                parms[1] = "nonce-send";
            }
            new HTTP_Query_Task(this, current_send_message.context).execute(parms);
            return;
        }
        //
        //get gas price
        long gas_price = preferences.getLong("gas_price", Util.DEFAULT_GAS_PRICE);
        long gas_price_refresh_sec = preferences.getLong("gas_price_refresh_sec", 0);
        if (gas_price <= 0 || now_sec - gas_price_refresh_sec > 60 * 60 * 1000) {
            //update gas price once per hour
            System.out.println("Payment_Processor: refresh gas price");
            String gas_URL = "https://etherchain.org/api/gasPrice";
            String parms[] = new String[2];
            parms[0] = gas_URL;
            parms[1] = "gas-send";
            new HTTP_Query_Task(this, current_send_message.context).execute(parms);
            return;
        }
        System.out.println("send_guts: gas price is: " + gas_price + "; gas limit: " + current_send_message.gas_limit);
        System.out.println("total cost of gas is: " + ((float)(current_send_message.gas_limit * gas_price) / Util.WEI_PER_ETH) + " ETH)");
        //
        // ready to broadxast tx
        System.out.println("Payment_Processor: broadcast tx -- size = " + current_send_message.size_wei + " (" + ((float)current_send_message.size_wei / Util.WEI_PER_ETH) + " ETH)");
        if ((encrypt_sec_history < 0 && mem_avail_kb < 640000) || mem_avail_kb < 320000 || encrypt_sec_history > 10) {
            //it usually takes more than 10 secs to encrypt tx. this happens on phones that don't have enough memory... cuz the encryption algo is memory intense
            //note: my samsung tablet must be running at 100% cpu while it does the encryption... so to get it to process the delay message, (and complete the deposit
            //animation) we need to sleep a bit here...
            current_send_message.client.interim_payment_result(current_send_message.size_wei, current_send_message.client_data, "encrypting");
            try { Thread.sleep((encrypt_sec_history > 10) ? 1500 : 1250); } catch (InterruptedException e) { }
        }
        long beg_create_transaction_sec = System.currentTimeMillis() / 1000;
	    String key = preferences.getString("key", "");
        String hex_tx = create_transaction(acct_addr, key, current_send_message.to_addr, current_send_message.size_wei,
                                           current_send_message.nonce, current_send_message.gas_limit, gas_price, current_send_message.data);
        long end_create_transaction_sec = System.currentTimeMillis() / 1000;
        long elapsed_encrypt_sec = end_create_transaction_sec - beg_create_transaction_sec;
        System.out.println("Payment_Processor::send_guts -- create_transaction took " + elapsed_encrypt_sec + " seconds");
        if (hex_tx.isEmpty()) {
            dispose_current_message("send", false, "", 0, "error encoding transaction!", true);
            return;
        }
        if (acct_has_no_txs) {
            SharedPreferences.Editor preferences_editor = preferences.edit();
            preferences_editor.putBoolean("acct_has_no_txs", false);
            preferences_editor.apply();
        }
        encrypt_sec_history = (encrypt_sec_history < 0) ? elapsed_encrypt_sec : (3 * encrypt_sec_history + elapsed_encrypt_sec) / 4;
        //in case of raw tx, we reurn the hex_tx in the txid parm; that's as far as we go.... so update all preference vars as if we just
        //broadcast the transaction
        if (current_send_message.want_raw_tx) {
            //we will update balance, last_tx_nonce, last_pay_sec, refresh_mode, in the dispose_current_message fcn, iff the client broadcasts
            //the tx successfully
            long balance = preferences.getLong("balance", 0);
            long est_cost = current_send_message.size_wei + (current_send_message.gas_limit * gas_price);
            dispose_current_message("send", true, hex_tx, balance - est_cost, "", false);
            return;
        }
        String broadcast_URL = "https://api.etherscan.io/api?module=proxy&action=eth_sendRawTransaction&hex=" + hex_tx;
        String parms[] = new String[2];
        parms[0] = broadcast_URL;
        parms[1] = "broadcast-send";
        new HTTP_Query_Task(this, current_send_message.context).execute(parms);
        return;
    }


    private String create_transaction(String acct_addr, String key, String to_addr, long size_wei,
                                      long nonce, long gas_limit, long gas_price, byte data[]) {
        String to_addr_no_0x = to_addr.startsWith("0x") ? to_addr.substring(2) : to_addr;
        //create signed transaction
        byte[] bytes_key = null;
        byte[] bytes_to_addr = null;
        try {
            bytes_key = hex.decode(key.getBytes());
            bytes_to_addr = hex.decode(to_addr_no_0x.getBytes());
        } catch (DecoderException e) {
            System.out.println("payment_processor: create_transaction decoder exception: " + e.toString());
            return("");
        }
        byte[] bytes_gas_price = longToBytesNoLeadZeroes(gas_price);
        byte[] bytes_gas_limit = longToBytesNoLeadZeroes(gas_limit);
        byte[] bytes_value = longToBytesNoLeadZeroes(size_wei);
        byte[] bytes_nonce = longToBytesNoLeadZeroes(nonce);
        Transaction tx = new Transaction(bytes_nonce, bytes_gas_price, bytes_gas_limit, bytes_to_addr, bytes_value, data);
        //
        tx.sign(bytes_key);
        byte[] rlp_encoded_tx = tx.getEncoded();
        String hex_tx = new String(hex.encodeHex(rlp_encoded_tx));
        return hex_tx;
    }


    /* ------------------------------------------------------------------------------------------------------------------------
    handle all http responses
    ------------------------------------------------------------------------------------------------------------------------ */
    private void call_next_callback(final String next_callback, final int delay_sec) {
        if (delay_sec > 0) {
            synchronized (monitor) {
                long now_sec = System.currentTimeMillis() / 1000;
                Payment_Message payment_message = next_callback.equals("refresh") ? current_balance_message : current_send_message;
                payment_message.is_suspended = true;
                payment_message.wait_until_sec = now_sec + delay_sec;
                monitor.notify();
            }
        } else {
            if (next_callback.equals("refresh"))
                refresh_balance_guts();
            else
                send_guts();
        }
    }


    public void handle_http_rsp(String callback, String rsp) {
        long now_sec = System.currentTimeMillis() / 1000;
        int next_idx = callback.indexOf("-");
        String next_callback = (next_idx >= 0) ? callback.substring(next_idx + 1) : "";
        System.out.println("Payment_Processor::handle_http_rsp got " + callback + " callback, now_sec = " + now_sec);
        //
        // -- deal with gas price
        // { "status": 1, "data": [ { "price": 23000000000 } ] }
        //
        if (callback.startsWith("gas")) {
            long gas_price = 0;
            if (rsp.contains("price")) {
                String gas_price_str = Util.json_parse(rsp, "price");
                if (!gas_price_str.isEmpty()) {
                    gas_price = Long.valueOf(gas_price_str);
                    long max_price = 0;
                    long min_price = Long.MAX_VALUE;
                    for (int i = 0; i < gas_price_history.length; ++i) {
                        gas_price_history[i] = (i < gas_price_history.length - 1) ? gas_price_history[i + 1] : gas_price;
                        if (gas_price_history[i] > max_price)
                            max_price = gas_price_history[i];
                        if (gas_price_history[i] < min_price)
                            min_price = gas_price_history[i];
                    }
                    SharedPreferences.Editor preferences_editor = preferences.edit();
                    preferences_editor.putLong("gas_price", min_price);
                    //only update timestamp after we have collected several samples. that way we initially collect samples for
                    //every payment
                    if (max_price < Long.MAX_VALUE)
                        preferences_editor.putLong("gas_price_refresh_sec", now_sec);
                    preferences_editor.apply();
                    call_next_callback(next_callback, 0);
                    return;
                }
            }
            System.out.println("payment_processor: eror retrieving gas price");
            dispose_current_message(next_callback, false, "", 0, "error retrieving gas price!", true);
            return;
        }

        //
        // -- deal with nonce
        // { "status": 1, "data": [ { "accountNonce": "16" } ] }
        //
        if (callback.startsWith("nonce")) {
            //note that first payment nonce is 0; so if you've never made a payment then we have a convention that our "last-used-nonce" is -1.
            //lt -1 means we've never even tried to update
            long nonce = -2;
            if (rsp.contains("accountNonce")) {
                String nonce_str = Util.json_parse(rsp, "accountNonce");
                if (!nonce_str.isEmpty())
                    nonce = Long.valueOf(nonce_str);
            } else if (rsp.contains("status")) {
                String status_str = Util.json_parse(rsp, "status");
                if (status_str.equals("1")) {
                    //no error, but no nonce data.... the account has never been used
                    nonce = -1;
                }
                System.out.println("status = \"" + status_str + "\"; nonce = " + nonce);
            }
            if (nonce != -2) {
                update_nonce_logic(nonce, next_callback, false);
                return;
            }
            System.out.println("payment_processor: eror retrieving nonce");
            dispose_current_message(next_callback, false, "", 0, "error retrieving nonce!", true);
            return;
        }


        //
        // -- deal with transactions (to extract nonce)
        // { "status": 1, "data": [
        //	 { "hash": "0x1b3dca103e0605b45f81eede754401df4082b87c4faf3f1205755b36f1b34ddf",
        //     "sender": "0x8b8a571730b631f58e7965d78582eae1b0417ab6",
        //	   "recipient": "0x85d9147b0ec6d60390c8897244d039fb55b087c6",
        //	   "accountNonce": "76",
        //	   "price": 25000000000, "gasLimit": 35000, "amount": 2000000108199936, "block_id": 2721132,
        //	   "time": "2016-11-30T09:58:07.000Z", "newContract": 0, "isContractTx": null,
        //	   "blockHash": "0x5c1118c94176902cab1783f8d4f8d17544c7a16c8ef377f674fa89693eb3ab0c",
        //	   "parentHash": "0x1b3dca103e0605b45f81eede754401df4082b87c4faf3f1205755b36f1b34ddf",
        //	   "txIndex": null, "gasUsed": 21000, "type": "tx"
        //	   },
        //	   .....
        //   }
        if (callback.startsWith("transactions")) {
            String status_str = "";
            if (rsp.contains("status")) {
                status_str = Util.json_parse(rsp, "status");
                int idx = rsp.indexOf("status") + "status".length();
                rsp = rsp.substring(idx);
            }
            if (!status_str.equals("1")) {
                System.out.println("payment_processor: error retrieving transactions: " + rsp);
                dispose_current_message(next_callback, false, "", 0, "error retrieving transactions!", true);
                return;
            }
            long best_nonce = -1;
            String acct_addr = preferences.getString("acct_addr", "");
            for (int i = 0, idx = 0; i < 100; ++i) {
                if (rsp.contains("{")) {
                    idx = rsp.indexOf('{') + 1;
                    rsp = rsp.substring(idx);
                } else {
                    break;
                }
                if (rsp.contains("sender")) {
                    String sender = Util.json_parse(rsp, "sender");
                    if (!sender.equals(acct_addr))
                        continue;
                }
                if (rsp.contains("accountNonce")) {
                    String nonce_str = Util.json_parse(rsp, "accountNonce");
                    if (!nonce_str.isEmpty()) {
                        long nonce = Long.valueOf(nonce_str);
                        if (nonce > best_nonce)
                            best_nonce = nonce;
                    }
                }
                if (rsp.contains("}")) {
                    idx = rsp.indexOf('}') + 1;
                    rsp = rsp.substring(idx);
                } else {
                    break;
                }
            }
            //have best nonce... see if it beats out current verified nonce
            update_nonce_logic(best_nonce, next_callback, true);
            return;
        }


        //
        // -- deal with broadcast
        // { "jsonrpc": "2.0", "result": "0xd22456131597cff2297d1034f9e6f790e9678d85c041591949ab5a8de5f73f04", "id": 1 }
        // alternately:
        // { "jsonrpc":"2.0","error": {"code":-32010, "message": "Transaction nonce is too low. Try incrementing the nonce.","data": null},"id":1 }
        //
        if (callback.startsWith("broadcast")) {
            String txid = "";
            if (rsp.contains("result")) {
                txid = Util.json_parse(rsp, "result");
                if (BuildConfig.DEBUG)
                    System.out.println("txid: " + txid);
                if (!txid.isEmpty()) {
                    long balance = preferences.getLong("balance", 0);
                    long gas_price = preferences.getLong("gas_price", Util.DEFAULT_GAS_PRICE);
                    long est_cost = current_send_message.size_wei + (current_send_message.gas_limit * gas_price);
                    SharedPreferences.Editor preferences_editor = preferences.edit();
                    preferences_editor.putLong("last_tx_nonce", current_send_message.nonce);
                    preferences_editor.putLong("last_pay_sec", now_sec);
                    long oldest_unverified_tx = preferences.getLong("oldest_unverified_tx", 0);
                    long verified_nonce_changed_sec = preferences.getLong("verified_nonce_changed_sec", 0);
                    //oldest-verified-tx isn't really the oldest-*verified*-tx; it's actually the oldest tx since the verified nonce last changed.
                    //it's useful to indicate how long the nonce has been stuck.
                    if (oldest_unverified_tx < verified_nonce_changed_sec)
                        preferences_editor.putLong("oldest_unverified_tx", now_sec);
                    balance -= est_cost;
                    preferences_editor.putLong("balance", balance);
                    preferences_editor.putBoolean("refresh_mode", true);
                    preferences_editor.apply();
                    dispose_current_message(next_callback, true, txid, balance, "", false);
                    return;
                }
            }
            if (rsp.contains("error")) {
                String err_msg = Util.json_parse(rsp, "message");
                System.out.println(rsp + "; msg: " + err_msg);
                if (err_msg.contains("nonce is too low")) {
                    SharedPreferences.Editor preferences_editor = preferences.edit();
                    //during developement i managed to set the last_tx_nonce incorrectly. that should be a condition that we should be able to recover
                    //from.... so here if we get a nonce-too-low message, and the verified nonce is gt. our last-tx-nonce, then just inc. the verified
                    //nonce. this corrects the case in which the nonce api reurns an incorrect nonce (cuz of rapid-fire tx's) -- you can't correct that
                    //unless you get the nonce from transactions -- and we won't do that unless the last-tx-nonce is gt. the verified nonce.
                    //if the verified nonce is not eq. to last nonce, then just reset to initial value.
                    long last_tx_nonce = preferences.getLong("last_tx_nonce", -1);
                    long verified_nonce = preferences.getLong("verified_nonce", -2);
                    verified_nonce = (last_tx_nonce == verified_nonce) ? last_tx_nonce + 1 : -2;
                    preferences_editor.putLong("verified_nonce", verified_nonce);
                    preferences_editor.putLong("verified_nonce_changed_sec", now_sec);
                    preferences_editor.apply();
                    System.out.println("force check nonce...");
                    call_next_callback(next_callback, 0);
                    return;
                }
            }
            System.out.println("payment_processor: eror broadcasting transaction: " + rsp);
            dispose_current_message(next_callback, false, "", 0, "error broadcasting transaction!", true);
            return;
        }

        //
        // -- deal with balance
        // { "status": 1, "data": [
        //   { "address": "0x7223efbf783eba259451a89e8e84c26611df8c4f", "balance": 40038159108626850000, "nonce": null, "code": "0x", "name": null, "storage": null, "firstSeen": null }
        //  ] }
        //
        if (callback.startsWith("balance")) {
            long new_balance = -1;
            boolean got_balance = false;
            String balance_str = Util.json_parse(rsp, "balance");
            if (BuildConfig.DEBUG)
                System.out.println("balance: " + balance_str);
            if (!balance_str.isEmpty() && !balance_str.equals("null")) {
                new_balance = Long.valueOf(balance_str);
            } else if (rsp.contains("status")) {
                String status_str = Util.json_parse(rsp, "status");
                if (status_str.equals("1")) {
                    //no error, but no balance data.... the account has never been used
                    new_balance = 0;
                }
            }
            if (new_balance >= 0) {
                long verified_balance = preferences.getLong("verified_balance", 0);
                long verified_balance_changed_sec = preferences.getLong("verified_balance_changed_sec", 0);
                SharedPreferences.Editor preferences_editor = preferences.edit();
                if (verified_balance != new_balance) {
                    System.out.println("changing verified_balance from " + verified_balance + " to " + new_balance);
                    verified_balance = new_balance;
                    verified_balance_changed_sec = now_sec;
                    preferences_editor.putLong("verified_balance", verified_balance);
                    preferences_editor.putLong("verified_balance_changed_sec", verified_balance_changed_sec);
                    preferences_editor.apply();
                }
                long balance = preferences.getLong("balance", 0);
                long last_tx_nonce = preferences.getLong("last_tx_nonce", -1);
                long verified_nonce = preferences.getLong("verified_nonce", -2);
                long balance_diff = Math.abs(verified_balance - balance);
                long balance_refresh_sec = preferences.getLong("balance_refresh_sec", 0);
                if (last_tx_nonce == verified_nonce && verified_balance > balance) {
                    System.out.println("last_tx_nonce = verified_nonce = " + verified_nonce + ", and balance has gone up");
                    System.out.println("adopting verified_balance");
                    preferences_editor.putLong("balance", verified_balance);
                    preferences_editor.apply();
                    balance = verified_balance;
                } else if (now_sec - verified_balance_changed_sec > BALANCE_FORCED_ADOPTION_SEC ||
                           balance_diff <= BALANCE_ESTIMATE_SLOP && now_sec - balance_refresh_sec < BALANCE_FORCED_ADOPTION_SEC/2) {
                    if (balance_diff > BALANCE_ESTIMATE_SLOP) {
                        //balance has not changed in a long time... and it still is not equal to our estimated balance... apparently someone
                        //has deposited funds, or otherwise used this acct external to our program; so we just adopt the verified balance.
                        System.out.println("hey! estimated balance = " + balance + ", but verified_balance = " + verified_balance +
                                        "; hasn't changed in " + (now_sec - verified_balance_changed_sec) + " sec");
                        System.out.println("adopting verified_balance");
                    }
                    preferences_editor.putLong("balance", verified_balance);
                    preferences_editor.apply();
                    balance = verified_balance;
                }
                preferences_editor.putLong("balance_refresh_sec", now_sec);
                preferences_editor.apply();
                if (balance == verified_balance) {
                    call_next_callback(next_callback, 0);
                } else {
                    System.out.println("check balance again in 5 sec");
                    call_next_callback(next_callback, 5);
                }
                return;
            }
            System.out.println("payment_processor: eror retrieving balance: " + rsp);
            dispose_current_message(next_callback, false, "", 0, "error retrieving balance!", true);
            return;
        }

        //
        // -- deal with price
        // {
        //     "difficulty": { "number": 2953280, "coinbase": "0xea674fdde714fd979de3edf0f56aa9716b898ec8", "time": "2017-01-07T17:25:54.000Z",
        //                     "difficulty": 92850780236842, "gasUsed": 538482, "uncle_count": 0, "blockTime": 21, "name": "ethermine", "gasLimit": 4015639 },
        //     "txCount": { "count": 1673 },
        //     "blockCount": { "number": 2953280, "coinbase": "0xea674fdde714fd979de3edf0f56aa9716b898ec8", "time": "2017-01-07T17:25:54.000Z", "difficulty": 92850780236842,
        //                     "gasUsed": 538482, "uncle_count": 0, "blockTime": 21, "name": "ethermine", "gasLimit": 4015639 },
        //     "blocks": [
        //           { "number": 2953280, "coinbase": "0xea674fdde714fd979de3edf0f56aa9716b898ec8", "time": "2017-01-07T17:25:54.000Z", "difficulty": 92850780236842,
        //             "gasUsed": 538482, "uncle_count": 0, "blockTime": 21, "name": "ethermine", "gasLimit": 4015639  },
        //           ....
        //      ]
        //     "txs": [
        //          { "hash": "0x30c10a0a80907c7117826e4b0b3a3c175a86a2b4cc39bb1df0d242b8999185bb", "parentHash": "0x30c10a0a80907c7117826e4b0b3a3c175a86a2b4cc39bb1df0d242b8999185bb",
        //            "block_id": 2953280, "sender": "0x563133f772e3ee25708153b629efce0f13610862", "senderName": null, "recipient": "0x209c4784ab1e8183cf58ca33cb740efbf3fc18ef",
        //            "recipientName": null, "amount": 1089969320000000000, "time": "2017-01-07T17:25:54.000Z" },
        //           ....
        //      ]
        //    "price": { "usd": 9.71, "btc": 0.01291 },
        //    "stats": { "blockTime": 14.0749, "difficulty": 71472687483186.2, "hashRate": 5270984772944.684, "uncle_rate": 0.0686 }
        // }
        //
        if (callback.startsWith("price")) {
            float usd_price = -1;
            if (rsp.contains("price")) {
                int price_field_idx = rsp.indexOf("price") + "price".length();
                rsp = rsp.substring(price_field_idx);
                String price_str = Util.json_parse(rsp, "usd");
                if (BuildConfig.DEBUG)
                    System.out.println("price: " + price_str);
                if (!price_str.isEmpty() && !price_str.equals("null")) {
                    try { usd_price = Float.valueOf(price_str); }
                    catch (NumberFormatException e) { System.out.println(e.toString()); }
                }
            }
            if (usd_price >= 0) {
                SharedPreferences.Editor preferences_editor = preferences.edit();
                preferences_editor.putFloat("usd_price", usd_price);
                preferences_editor.putLong("price_refresh_sec", now_sec);
                preferences_editor.apply();
                refresh_balance_guts();
                return;
            }
            System.out.println("payment_processor: eror retrieving price: " + rsp);
            dispose_current_message(next_callback, false, "", 0, "error retrieving price!", true);
            return;
        }

        //
        // -- huh? what is this?
        //
        System.out.println("Payment_Processor::handle_http_rsp: Hey! should never get here. callback = " + callback);
    }



    private void update_nonce_logic(long nonce, String next_callback, boolean from_transactions) {
        long now_sec = System.currentTimeMillis() / 1000;
        long last_tx_nonce = preferences.getLong("last_tx_nonce", -1);
        long last_pay_sec = preferences.getLong("last_pay_sec", 0);
        long nonce_refresh_sec = preferences.getLong("nonce_refresh_sec", 0);
        long nonce_from_txs_refresh_sec = preferences.getLong("nonce_from_txs_refresh_sec", 0);
        long verified_nonce = preferences.getLong("verified_nonce", -2);
        long verified_nonce_changed_sec = preferences.getLong("verified_nonce_changed_sec", 0);
        long oldest_unverified_tx = preferences.getLong("oldest_unverified_tx", 0);
        SharedPreferences.Editor preferences_editor = preferences.edit();
        if (verified_nonce > nonce) {
            //using the get-nonce api from etherchain.org, they really just give us the nonce used in the last recorded transaction. so when we do
            //rapid-fire tx's, the order of the tx's can get swapped, and then they give us the wrong nonce. we can find the real nonce by retrieving
            //recent transactions and using the highest value. bottom line is that we protect against the verified nonce going down here.
            nonce = verified_nonce;
        }
        if (verified_nonce != nonce) {
            System.out.println("changing verified_nonce from " + verified_nonce + " to " + nonce);
            verified_nonce = nonce;
            preferences_editor.putLong("verified_nonce", verified_nonce);
            preferences_editor.putLong("verified_nonce_changed_sec", now_sec);
            preferences_editor.apply();
        } else if (verified_nonce < last_tx_nonce  &&
                   now_sec - verified_nonce_changed_sec > NONCE_FORCED_ADOPTION_SEC &&
                   now_sec - oldest_unverified_tx > NONCE_FORCED_ADOPTION_SEC &&
                   now_sec - nonce_from_txs_refresh_sec < (now_sec - oldest_unverified_tx) - NONCE_FORCED_ADOPTION_SEC) {
            //nonce has not changed in a long time... and it still is not equal to the last nonce that we used... and last time we checked was past the interval by which it definately
            //should have changed -- so apparently some tx failed; so we just adopt the verified nonce. when we do that, we make it appear as if the verified nonce changed right
            //now. this way we won't keep on afopting the same verified nonce over and over; that is, we need to wait at least another NONCE_FORCED_ADOPTION_SEC.
            System.out.println("hey! last_tx_nonce = " + last_tx_nonce + ", but verified_nonce = " + verified_nonce +
                        "; hasn't changed in " + (now_sec - verified_nonce_changed_sec) + " sec");
            System.out.println("nonce_refresh_sec = " + nonce_refresh_sec + "; verified_nonce_changed_sec = " + verified_nonce_changed_sec);
            System.out.println("adopting verified_nonce");
            preferences_editor.putLong("last_tx_nonce", verified_nonce);
            preferences_editor.putLong("verified_nonce_changed_sec", now_sec);
            preferences_editor.apply();
        } else if (verified_nonce < last_tx_nonce) {
            System.out.println("hey! last_tx_nonce = " + last_tx_nonce + ", but verified_nonce = " + verified_nonce);
            System.out.println("not adopting verified_nonce: ");
            System.out.println("nonce_refresh_sec = " + nonce_refresh_sec + "; verified_nonce_changed_sec = " + verified_nonce_changed_sec);
        }
        preferences_editor.putLong("nonce_refresh_sec", now_sec);
        if (from_transactions)
            preferences_editor.putLong("nonce_from_txs_refresh_sec", now_sec);
        preferences_editor.apply();
        if (verified_nonce >= last_tx_nonce) {
            //any time the verified nonce is gt. our last_tx, we alway adopt the verified nonce....
            preferences_editor.putLong("nonce_settled_sec", now_sec);
            preferences_editor.putBoolean("tx_err_occurred", false);
            preferences_editor.apply();
            if (verified_nonce > last_tx_nonce) {
                preferences_editor.putLong("last_tx_nonce", verified_nonce);
                preferences_editor.apply();
            }
            call_next_callback(next_callback, 0);
        } else {
            //nonce is not up-to-date. we used to wait 7 seconds here, and then try to update again. but if this is a tx, and if rapid=tx is enabled, and the next tx
            //would qualify as a rapid tx, then we can do the next callback immediately.
            if (next_callback.startsWith("send") && current_send_message != null) {
                boolean enable_rapid_tx =  current_send_message.context.getResources().getBoolean(R.bool.enable_rapid_tx);
                long nonce_settled_sec = preferences.getLong("nonce_settled_sec", 0);
                //if a) we've made payment(s) since nonce was last settled
                //   b) those payments were within the typical ether transaction time
                //   c) we tried updating nonce recently
                //then go ahead and just use our last-tx-nonce.... otherwise we better wait for the verified nonce
                if (last_pay_sec >= nonce_settled_sec && now_sec - last_pay_sec < LONG_ETHER_TRANSACTION_TIME_SEC && enable_rapid_tx) {
                    call_next_callback(next_callback, 0);
                    return;
                }
                current_send_message.client.interim_payment_result(current_send_message.size_wei, current_send_message.client_data, "waiting for nonce");
            }
            System.out.println("check nonce again in 5 sec");
            call_next_callback(next_callback, 5);
        }
    }

}
