package com.bringcommunications.etherpay;

/**
 * Created by dbrosen on 11/25/16.
 */

public interface HTTP_Query_Client {
    public void handle_http_rsp(String callback, String rsp);
}
