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
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import static android.text.format.DateUtils.*;

/**
 * Created by dbrosen on 12/2/16.
 */

class Transaction_Array_Adapter extends ArrayAdapter<Transaction_Info> {
    private Context context;
	private Transaction_Info values[];
	private String acct_addr;

	public Transaction_Array_Adapter(Context context, Transaction_Info values[], String acct_addr) {
		super(context, R.layout.transaction_list_item, values);
		this.context = context;
		this.values = values;
		this.acct_addr = acct_addr;
	}

    public View getView(int position, View convertView, ViewGroup parent) {
        String date_str = "";
		String txid = "";
		String addr = "";
		float size = 0;
		int icon = -1;

		if (values != null) {
			Transaction_Info transaction_info = values[position];
			date_str = formatDateTime(context, transaction_info.date.getTimeInMillis(), FORMAT_SHOW_DATE | FORMAT_SHOW_TIME | FORMAT_NUMERIC_DATE);
			txid = "txid: " + transaction_info.txid;
			size = transaction_info.size;
			if (acct_addr.equals(transaction_info.to)) {
				addr = "from: " + transaction_info.from;
				icon = R.drawable.ic_savings;
			} else if (acct_addr.equals(transaction_info.from)) {
				addr = "to: " + transaction_info.to;
				icon = R.drawable.ic_etherpay;
			} else {
				addr = "transaction to/from unrelated addresses!";
			}
        }
		LayoutInflater inflater = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		View row_view = inflater.inflate(R.layout.transaction_list_item, parent, false);
        ImageView icon_view = (ImageView)row_view.findViewById(R.id.icon);
		if (icon >= 0)
        	icon_view.setImageResource(icon);
		TextView date_view = (TextView)row_view.findViewById(R.id.date);
		date_view.setText(date_str);
        TextView amount_view = (TextView)row_view.findViewById(R.id.amount);
        amount_view.setText(String.format("%7.05f", size) + " ETH");
        TextView txid_view = (TextView)row_view.findViewById(R.id.txid);
        txid_view.setText(txid);
		TextView addr_view = (TextView)row_view.findViewById(R.id.addr);
		addr_view.setText(addr);
		return(row_view);
	}


}
