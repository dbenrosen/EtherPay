package com.bringcommunications.etherpay;

import java.util.Calendar;

/**
 * Created by dbrosen on 12/2/16.
 *
 * could include things like:
 *
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
 */

class Transaction_Info {
    public String txid;
    public String to;
    public String from;
    public long nonce;
    public float size;
    public Calendar date;
    Transaction_Info(String txid, String to, String from, long nonce, float size, Calendar date) {
        this.txid = txid;
        this.to = to;
        this.from = from;
        this.nonce = nonce;
        this.size = size;
        this.date = date;
    }
}
