package com.shauryachats.synctext;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.util.SparseArray;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import android.Manifest;

import com.google.android.gms.vision.Frame;
import com.google.android.gms.vision.text.TextBlock;
import com.google.android.gms.vision.text.TextRecognizer;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;


public class MainActivity extends AppCompatActivity {

    Button scan, connect;
    boolean isConnected = false;
    String server_ip = null;
    int server_port = -1;
    private static final int QR_REQUEST_CODE = 1;
    private static final String TAG = "Hixy";
    TextView textView;
    Activity activity;

    // Storage Permissions
    private static final int REQUEST_EXTERNAL_STORAGE = 1;
    private static String[] PERMISSIONS_STORAGE = {
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
    };
    private Bitmap mImageBitmap;

    /**
     * Checks if the app has permission to write to device storage
     *
     * If the app does not has permission then the user will be prompted to grant permissions
     *
     * @param activity
     */
    public static void verifyStoragePermissions(Activity activity) {
        // Check if we have write permission
        int permission = ActivityCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE);

        if (permission != PackageManager.PERMISSION_GRANTED) {
            // We don't have permission so prompt the user
            ActivityCompat.requestPermissions(
                    activity,
                    PERMISSIONS_STORAGE,
                    REQUEST_EXTERNAL_STORAGE
            );
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        scan = findViewById(R.id.scan);
        connect = findViewById(R.id.connect);
        textView = findViewById(R.id.textView);

        activity = this;

//        verifyStoragePermissions(this);

        scan.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                takePhoto(activity);
            }
        });

        connect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (isConnected == false) {
                    Intent intent = new Intent(MainActivity.this, QRScanner.class);
                    intent.putExtra("server_ip", server_ip);
                    intent.putExtra("server_port", server_port);
                    startActivityForResult(intent, QR_REQUEST_CODE);
                } else {
                    isConnected = false;
                    textView.setText("Disconnected.");
                    connect.setText("CONNECT");
                    if (mTcpClient != null) {
                        mTcpClient.stopClient();
                    }
                }
            }

        });

    }

    File file;
    Uri fileUri;
    public static final int RC_TAKE_PHOTO = 56;

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        /*** QR ACTIVITY **/
        if (resultCode == RESULT_OK && requestCode == QR_REQUEST_CODE) {
            if (data.hasExtra("qrval")) {
                Log.d(TAG, "hello");
                String qrdata = data.getExtras().getString("qrval");
                Log.d(TAG, qrdata);
                String ar[] = qrdata.split(",");
                server_ip = ar[0];
                server_port = Integer.parseInt(ar[1]);
                Log.d(TAG, server_ip + " " + server_port);

                textView.setText("Connected to " + server_ip + ":" + server_port);
                connect.setText("DISCONNECT");
                isConnected = true;

                new ConnectTask().execute("");
            }
        }
        else if (resultCode == RESULT_OK && requestCode == RC_TAKE_PHOTO)
        {
            try {
                mImageBitmap = MediaStore.Images.Media.getBitmap(this.getContentResolver(), fileUri);

                // EXTRACT OCR AND SEND MESSAGE
                String OCRed = OCRHere();
//                Toast.makeText(MainActivity.this, OCRed, Toast.LENGTH_SHORT).show();
                Log.d(TAG, server_ip + " " + server_port);
//                MessageSender messageSender = new MessageSender(server_ip, server_port);
//                messageSender.execute(OCRed);
                if (mTcpClient != null) {
                    mTcpClient.sendMessage(OCRed);
                }
//                if (mTcpClient != null) {
//                    mTcpClient.stopClient();
//                }
                Toast.makeText(MainActivity.this, OCRed, Toast.LENGTH_SHORT).show();

            } catch (IOException e) {
                Log.d(TAG, "Oops!");
                e.printStackTrace();
            }
            Log.d(TAG, "Here in onActResu1!");
        }
    }

    private void takePhoto(Activity activity) {
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        file = new File(activity.getExternalCacheDir(),
                String.valueOf(System.currentTimeMillis()) + ".jpg");
        fileUri = Uri.fromFile(file);
        intent.putExtra(MediaStore.EXTRA_OUTPUT, fileUri);
        activity.startActivityForResult(intent, RC_TAKE_PHOTO);
    }

    protected String OCRHere() {

        Bitmap bitmap = mImageBitmap;

        TextRecognizer textRecognizer = new TextRecognizer.Builder(this).build();
        try {
            if (!textRecognizer.isOperational()) {
                new AlertDialog.
                        Builder(this).
                        setMessage("Text recognizer could not be set up on your device").show();
            }

            Frame frame = new Frame.Builder().setBitmap(bitmap).build();
            SparseArray<TextBlock> origTextBlocks = textRecognizer.detect(frame);
            List<TextBlock> textBlocks = new ArrayList<>();
            for (int i = 0; i < origTextBlocks.size(); i++) {
                TextBlock textBlock = origTextBlocks.valueAt(i);
                textBlocks.add(textBlock);
            }
            Collections.sort(textBlocks, new Comparator<TextBlock>() {
                @Override
                public int compare(TextBlock o1, TextBlock o2) {
                    int diffOfTops = o1.getBoundingBox().top - o2.getBoundingBox().top;
                    int diffOfLefts = o1.getBoundingBox().left - o2.getBoundingBox().left;
                    if (diffOfTops != 0) {
                        return diffOfTops;
                    }
                    return diffOfLefts;
                }
            });

            StringBuilder detectedText = new StringBuilder();
            for (TextBlock textBlock : textBlocks) {
                if (textBlock != null && textBlock.getValue() != null) {
                    detectedText.append(textBlock.getValue());
                    detectedText.append("\n");
                }
            }

            return detectedText.toString();
        } finally {
            textRecognizer.release();
        }
    }

    TcpClient mTcpClient;
    String serverResponse;

    public class ConnectTask extends AsyncTask<String, String, TcpClient> {

        @Override
        protected TcpClient doInBackground(String... message) {

            //we create a TCPClient object
            mTcpClient = new TcpClient(new TcpClient.OnMessageReceived() {
                @Override
                //here the messageReceived method is implemented
                public void messageReceived(String message) {
                    //this method calls the onProgressUpdate
                    publishProgress(message);
                }
            }, server_ip, server_port);
            mTcpClient.run();

            return null;
        }

        @Override
        protected void onProgressUpdate(String... values) {
            super.onProgressUpdate(values);
            //response received from server
            Log.d(TAG, "response " + values[0]);
            //process server response here....

        }
    }
}
