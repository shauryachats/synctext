package com.shauryachats.synctext;

import android.nfc.Tag;
import android.os.AsyncTask;
import android.os.Message;
import android.util.Log;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

/**
 * Created by shauryachats on 1/2/18.
 */

public class MessageSender extends AsyncTask<String, Void, Void>
{
    Socket s;
    PrintWriter pw;
    String server_ip;
    int server_port;

    MessageSender(String Server_ip, int Server_port)
    {
        server_ip = Server_ip;
        server_port = Server_port;
    }

    @Override
    protected Void doInBackground(String... strings) {

        String message = strings[0];

        try {
            s = new Socket(server_ip, server_port);
            pw = new PrintWriter(s.getOutputStream());
            pw.write(message);
            pw.flush();
            pw.close();
            s.close();

        } catch (IOException e) {
            e.printStackTrace();
        }


        return null;
    }
}
