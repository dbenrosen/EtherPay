package com.bringcommunications.etherpay;

/**
 * Created by dbrosen on 1/3/17.
 */
public interface Payment_Processor_Client {
    public boolean payment_result(boolean ok, String txid, long size_wei, String client_data, String error);
    public void balance_result(boolean ok, long balance, String error);
    public void interim_payment_result(long size_wei, String client_data, String msg);
    public void interim_balance_result(String msg);
}
