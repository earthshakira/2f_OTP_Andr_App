package com.a2factor.earthshakira.otpverify;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.telephony.SmsMessage;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Scanner;

import static com.a2factor.earthshakira.otpverify.R.id.phone;

public class MainActivity extends AppCompatActivity {
    private BroadcastReceiver mReceiver;
    Button btn_receiver,btn_otp,btn_verify;
    EditText text_phone,text_otp;
    TextView tv;
    int state = 0,otp_receiver=0;
    int pre_words;
    String service_name = null;
    String session = null;
    String JSON = null;
    String[] btnTtitle={"START RECEIVER","STOP RECEIVER"};
    final IntentFilter intentFilter = new IntentFilter("android.provider.Telephony.SMS_RECEIVED");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        service_name = getString(R.string.SENDER_ID);
        pre_words = Integer.parseInt(getString(R.string.position));
        //UI stuff
        btn_receiver=(Button)findViewById(R.id.btn_broadcast);
        btn_otp=(Button)findViewById(R.id.btn_otp);
        btn_verify=(Button)findViewById(R.id.btn_verify);
        text_otp = (EditText)findViewById(R.id.otp);
        text_phone = (EditText)findViewById(phone);
        tv = (TextView)findViewById(R.id.texter);
        tv.setMovementMethod(new ScrollingMovementMethod());
        intentFilter.setPriority(999);


        btn_receiver.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggle_reciever();
            }
        });

        btn_otp.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String phone = String.valueOf(text_phone.getText());
                String regex = "\\d+";
                if(phone.matches(regex) && phone.length()==10){
                    //correct phone number
                    new httpGet().execute("https://2factor.in/API/V1/"+getString(R.string.API_KEY)+"/SMS/"+phone+"/"+getOTP());
                }else{
                    throwAlert("Number Error","Enter a proper number");
                }

            }
        });

        btn_verify.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new httpGet().execute("https://2factor.in/API/V1/"+getString(R.string.API_KEY)+"/SMS/VERIFY/"+session+"/"+ text_otp.getText().toString());
            }
        });

        /*
            mReceiver has the Broadcast Receiver required to get alerts about messages
         */
        mReceiver = new BroadcastReceiver() {
            String TAG = "Main Reciever";
            @Override
            public void onReceive(Context context, Intent intent) {
                // Get the data (SMS data) bound to intent
                Bundle bundle = intent.getExtras();

                //Log.d(TAG, "onReceive: "+runCMD("curl \"https://2factor.in/API/V1/e69de6e6-30bc-11e7-8473-00163ef91450/SMS/VERIFY/4f8a49ca-30cf-11e7-8473-00163ef91450/1234\""));
                SmsMessage[] msgs = null;

                String str = "";

                if (bundle != null) {
                    // Retrieve the SMS Messages received
                    Object[] pdus = (Object[]) bundle.get("pdus");
                    msgs = new SmsMessage[pdus.length];

                    // For every SMS message received
                    for (int i=0; i < msgs.length; i++) {
                        //for log
                        msgs[i] = SmsMessage.createFromPdu((byte[]) pdus[i]);
                        str += "SMS from " + msgs[i].getOriginatingAddress() + " : ";
                        str += msgs[i].getMessageBody().toString();
                        str += "\n";
                        //log ends
                        if(otp_receiver==1) {
                            try {
                                String send = msgs[i].getOriginatingAddress().split("-")[1];
                                if (send.equals(service_name)) {
                                    String OTP = msgs[i].getMessageBody().toString().split(" ")[pre_words];
                                    Log.d(TAG, "onReceive: "+OTP);
                                    text_otp.setText(OTP);
                                    toggle_reciever();
                                    otp_receiver = 0;
                                    return;
                                }
                            } catch (Exception e) {
                                continue;
                            }

                        }
                    }
                    addLog(str);
                    // Display the entire SMS Message
                    Log.d(TAG, str);
                }
            }
        };
    }

    String touchURL(String texturl){

        URL url = null;
        HttpURLConnection urlConnection=null;
        StringBuffer buff = null;
        try {
            url = new URL(texturl);
            urlConnection = (HttpURLConnection) url.openConnection();
            InputStream in = new BufferedInputStream(urlConnection.getInputStream());
            Scanner scr = new Scanner(in);
            buff = new StringBuffer();
            while(scr.hasNext()){
                buff.append(scr.next());
                Log.d("netop", "touchURL: "+buff.toString());
            }
        } catch (Exception e) {
            throwAlert("Network Error",e.getMessage());
            return null;
        } finally {
            urlConnection.disconnect();
        }
        return buff.toString();
    }

    private class httpGet extends AsyncTask<String, Integer, Long> {
        String TAG = "BG";
        protected Long doInBackground(String... urls) {
            long fetch=0,verify = 1; //workaround to know system state
            for(int i = 0 ; i<urls.length;i++) {
                JSON = touchURL(urls[i]);
                if(urls[i].contains("VERIFY")){
                    Log.d(TAG, "doInBackground: Verify TRUE");
                    return verify;
                }else{
                    Log.d(TAG, "doInBackground: Verify FALSE");
                    return fetch;
                }

            }
            return null;
        }

        @Override
        protected void onPostExecute(Long result) {
            Log.d("BG", "onPostExecute: "+result);
            addLog(JSON);
            try {
                JSONObject response = new JSONObject(JSON);
                if(result==1){//state where we need to verify OTP verification fetch
                    Log.d("BG", "onPostExecute: inside OTP Verification");
                    String status = response.getString("Status");
                    String details = response.getString("Details");
                    receiver_switch(0);
                    throwAlert("Verification Response","Status : "+status+"\nDetails"+details);
                }else if(result == 0){//OTP fetch
                    Log.d("BG", "onPostExecute: inside OTP Fetch");
                    String status = response.getString("Status");
                    if(!status.equals("Success"))throw new Exception("Response returned " + status +" Details : "+response.getString("Details"));
                    otp_receiver = 1;
                    session = response.getString("Details");
                    receiver_switch(1);
                    registerReceiver(mReceiver,intentFilter);
                }
            } catch (Exception e) {
                throwAlert("Something went wrong","Please check all details and retry");
                e.printStackTrace();
            }
        }
    }

    void throwAlert(String title,String message){
        AlertDialog alertDialog = new AlertDialog.Builder(MainActivity.this).create();
        alertDialog.setTitle(title);
        alertDialog.setMessage(message);
        alertDialog.setButton(AlertDialog.BUTTON_NEUTRAL, "OK",
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                });
        alertDialog.show();
        return;
    }

    void addLog(String x){
        tv.setText(x+"\n");
    }

    void toggle_reciever(){
        state = 1- state;
        if(state == 1) {
            registerReceiver(mReceiver, intentFilter);
        }
        else unregisterReceiver(mReceiver);
        btn_receiver.setText(btnTtitle[state]);
    }

    void receiver_switch(int i){
        /*
            1 -> receiver ON
            0 -> receiver OFF
         */
        if(i>1 || i < 0 || state == i)return;
        state = i;
        if(state == 1) {
            registerReceiver(mReceiver, intentFilter);
        }
        else unregisterReceiver(mReceiver);
        btn_receiver.setText(btnTtitle[state]);
    }

    String getOTP(){
        /*
            You can Write code here to generate your own API
            but we are going to use the AUTOGEN feature of the API
         */
        return "AUTOGEN";
    }
}

/*



 */