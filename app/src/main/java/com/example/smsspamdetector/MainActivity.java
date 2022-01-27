package com.example.smsspamdetector;

import static com.example.smsspamdetector.DatabaseManager.M_ID_ADDRESS_NAME;
import static com.example.smsspamdetector.DatabaseManager.M_ID_ID;
import static com.example.smsspamdetector.DatabaseManager.M_ID_IS_SPAM;
import static com.example.smsspamdetector.DatabaseManager.M_ID_LAST_MESSAGE;
import static com.example.smsspamdetector.DatabaseManager.M_ID_LAST_TIME;
import static com.example.smsspamdetector.DatabaseManager.M_ID_SERVICE_NUMBER;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.ActivityOptionsCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.smsspamdetector.Adapter.MsgListAdapter;
import com.example.smsspamdetector.Utils.CheckSpamUtils;
import com.example.smsspamdetector.Utils.MsgData;
import com.google.android.material.appbar.AppBarLayout;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

public class MainActivity extends AppCompatActivity implements SmsBroadcastReceiver.OnSmsGotListener {

    private static final int SMS_PERMISSION_CODE = 0;
    private static final String TAG = "MainExp";

    private SharedPreferences sharedPreferences;

    private DatabaseManager databaseManager;

    //private TextView msg;

    BroadcastReceiver broadcastReceiver;
    RecyclerView msgRecycler;
    MsgListAdapter msgListAdapter;

    CheckSpamUtils checkSpamUtils;

    EditText searchInput;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        final androidx.appcompat.widget.Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setTitle("");
        AppBarLayout appBarLayout = findViewById(R.id.app_bar);

        appBarLayout.addOnOffsetChangedListener(new AppBarLayout.OnOffsetChangedListener() {

            boolean isShow = false;
            int scrollRange = -1;

            @Override
            public void onOffsetChanged(AppBarLayout appBarLayout, int verticalOffset) {

                if (scrollRange == -1) {
                    scrollRange = appBarLayout.getTotalScrollRange();
                }
                if (scrollRange + verticalOffset == 0) {
                    isShow = true;
                    toolbar.setTitle("Messages");
                } else if (isShow) {
                    isShow = false;
                    toolbar.setTitle("");
                }

            }
        });


        //msg = findViewById(R.id.message_text);

        broadcastReceiver = new SmsBroadcastReceiver(this);
        final IntentFilter smsFilter = new IntentFilter("android.provider.Telephony.SMS_RECEIVED");
        smsFilter.setPriority(1000);
        this.registerReceiver(broadcastReceiver, smsFilter);

        databaseManager = new DatabaseManager(MainActivity.this);

        checkSpamUtils = new CheckSpamUtils(getApplicationContext(), MainActivity.this);

        msgRecycler = findViewById(R.id.msg_list);
        searchInput = findViewById(R.id.search_msg);

        msgRecycler.setLayoutManager(new LinearLayoutManager(this));

        searchInput.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                ActivityOptionsCompat optionsCompat = ActivityOptionsCompat
                        .makeSceneTransitionAnimation(MainActivity.this,
                                view,
                                "transition");

                Intent i = new Intent(MainActivity.this, SearchActivity.class);
                startActivity(i, optionsCompat.toBundle());

            }
        });
        if (!hasSmsPermission())
            showAlertDialog();
        else {

            BeginMsgGet();
        }

        //startActivity(new Intent(MainActivity.this, TestActivity.class));
    }

    private void BeginMsgGet() {
        sharedPreferences = getSharedPreferences("SMS_PREF", MODE_PRIVATE);

        GetSMS getSMS = new GetSMS();

        if (sharedPreferences.contains("isDatabaseCreated")) {
            if (sharedPreferences.getBoolean("isDatabaseCreated", true)) {
                getSMS.execute(1);
            }
        } else {
            getSMS.execute(0);

        }
    }

    @Override
    public void OnSmsGot(MsgData msgData) {

    }

    private class GetSMS extends AsyncTask<Integer, String, Boolean> {

        ArrayList<MsgData> msgData = new ArrayList<>();

        @Override
        protected void onPreExecute() {
            super.onPreExecute();

            msgListAdapter = new MsgListAdapter(msgData, MainActivity.this);
            msgRecycler.setAdapter(msgListAdapter);
        }

        @Override
        protected Boolean doInBackground(Integer... integers) {

            Log.e(TAG, "doInBackground: " + integers[0]);

            if (integers[0] == 0) {

                getNewMessage(null, 0, 0);
            } else {

                MsgData msgDataTemp = databaseManager.getMessagesFromDB();
                getNewMessage(msgDataTemp.getMsgTitle(), msgDataTemp.getMsgDateLong(), 1);

                Cursor cursor = databaseManager.getAllMsgAddressTable();

                UpdateAllMessages(cursor);
            }

            //if(postData.length()>0)
            //checkURLVulnerability();


            return msgData.size() > 0;
        }

        private void UpdateAllMessages(@NonNull Cursor cursor) {

            if (cursor.moveToFirst()) {

                int index_id = cursor.getColumnIndex(M_ID_ID);
                int index_title = cursor.getColumnIndex(M_ID_ADDRESS_NAME);
                int index_body = cursor.getColumnIndex(M_ID_LAST_MESSAGE);
                int index_long = cursor.getColumnIndex(M_ID_LAST_TIME);
                int index_spam = cursor.getColumnIndex(M_ID_IS_SPAM);
                int index_service = cursor.getColumnIndex(M_ID_SERVICE_NUMBER);


                do {

                    MsgData temp = new MsgData();

                    temp.setId(cursor.getString(index_id));
                    temp.setMsgTitle(cursor.getString(index_title));
                    temp.setMsgBody(cursor.getString(index_body));
                    temp.setMsgDateLong(cursor.getLong(index_long));
                    temp.setSpam(cursor.getInt(index_spam) != 0);
                    temp.setServiceNum(cursor.getString(index_service));

                    DateFormat dateFormat = new SimpleDateFormat("dd MMM yyyy HH:mm:ss");
                    Date date = new Date(cursor.getLong(index_long));

                    temp.setMsgDate(dateFormat.format(date));
                    msgData.add(temp);

                    publishProgress("done");

                } while (cursor.moveToNext());

            }

            if (!cursor.isClosed())
                cursor.close();

        }

        private void getNewMessage(@Nullable String msgTitle, long msgDateLong, int isNew) {

            final String SMS_URI_ALL = "content://sms/";

            try {

                Uri uri = Uri.parse(SMS_URI_ALL);
                String[] projection = new String[]{"_id", "address", "service_center", "body", "date", "type"};

                Cursor cur = getContentResolver().query(uri, projection, null, null, "date desc");

                if (cur.moveToNext()) {

                    int index_Address = cur.getColumnIndex("address");
                    int index_Person = cur.getColumnIndex("service_center");
                    int index_Body = cur.getColumnIndex("body");
                    int index_Date = cur.getColumnIndex("date");
                    int index_Type = cur.getColumnIndex("type");

                    do {

                        String strAddress = cur.getString(index_Address);
                        String intPerson = cur.getString(index_Person);
                        Log.d(TAG, "refreshSMS: " + intPerson);
                        String strbody = cur.getString(index_Body);
                        long longDate = cur.getLong(index_Date);

                        MsgData temp = new MsgData();
                        DateFormat dateFormat = new SimpleDateFormat("dd MMM yyyy HH:mm:ss");
                        Date date = new Date(longDate);

                        temp.setMsgDate(dateFormat.format(date));
                        temp.setMsgBody(strbody);
                        temp.setMsgTitle(strAddress);
                        temp.setMsgDateLong(longDate);
                        temp.setServiceNum(intPerson);

                        Log.d(TAG, "refreshSMS: " + intPerson);
                        if (isNew == 1) {
                            if (strAddress.equals(msgTitle) && longDate == msgDateLong) {
                                break;
                            } else {
                                parseMessage(temp, 1);
                            }
                        } else {
                            parseMessage(temp, 0);
                        }
                    } while (cur.moveToNext());

                    if (!cur.isClosed()) {
                        cur.close();
                        cur = null;
                    }

                }

            } catch (Exception e) {
                Log.d(TAG, "refreshSMS: " + e.getMessage());
            }

        }

        private void parseMessage(@NonNull MsgData temp, int isNew) {

            int messageAddressId = databaseManager.messageAddressPresent(temp.getMsgTitle());
            if (isNew == 0) {

                if (messageAddressId == -1) {

                    messageAddressId = databaseManager.insertAddressName(temp.getMsgTitle(),
                            temp.getMsgBody(),
                            temp.getMsgDateLong(),
                            temp.getServiceNum());
                    temp.setId(String.valueOf(messageAddressId));

                    msgData.add(temp);
                    publishProgress("done");

                } else {
                    temp.setId(String.valueOf(messageAddressId));
                }

                //isSpamMessage(messageId, temp);
            } else {

                if (messageAddressId == -1) {

                    messageAddressId = databaseManager.insertAddressName(temp.getMsgTitle(),
                            temp.getMsgBody(),
                            temp.getMsgDateLong(),
                            temp.getServiceNum());

                } else {

                    if (databaseManager.GetNewLong(messageAddressId) < temp.getMsgDateLong())
                        databaseManager.updateLastTime(messageAddressId, temp.getMsgDateLong(), temp.getMsgBody());
                }

                temp.setId(String.valueOf(messageAddressId));

                //isSpamMessage(messageId, temp);
            }
            int messageId = databaseManager.InsertMessages(temp, 0, 0);

        }

        @Override
        protected void onProgressUpdate(String... values) {
            super.onProgressUpdate(values);
            msgListAdapter.notifyDataSetChanged();

        }

        @Override
        protected void onPostExecute(Boolean s) {

            if (s) {
                msgListAdapter.notifyDataSetChanged();
            }

            try {

                checkSpamUtils.closeInputStream();

            } catch (Exception e) {

                Log.e(TAG, "onPostExecute: " + e.getMessage());

            }

            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putBoolean("isDatabaseCreated", true);
            editor.apply();
            editor.commit();
        }
    }

    private void showAlertDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Sms Permission");
        builder.setMessage("We need SMS read and receive permission to detect the spam in the message");
        builder.setPositiveButton("ok", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                dialogInterface.dismiss();
                requestPermission();

            }
        });
        builder.show();
    }

    private void requestPermission() {

        if (ActivityCompat
                .shouldShowRequestPermissionRationale(MainActivity.this,
                        Manifest.permission.READ_SMS)
                && ActivityCompat.shouldShowRequestPermissionRationale(MainActivity.this,
                Manifest.permission.READ_CONTACTS)
                && ActivityCompat.shouldShowRequestPermissionRationale(MainActivity.this,
                Manifest.permission.SEND_SMS))
            return;

        ActivityCompat.requestPermissions(MainActivity.this,
                new String[]{Manifest.permission.RECEIVE_SMS,
                        Manifest.permission.READ_SMS,
                        Manifest.permission.READ_CONTACTS,
                        Manifest.permission.SEND_SMS},
                SMS_PERMISSION_CODE);

    }

    private boolean hasSmsPermission() {

        return ContextCompat.checkSelfPermission(MainActivity.this,
                Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED
                && ContextCompat.checkSelfPermission(MainActivity.this
                , Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED
                && ContextCompat.checkSelfPermission(MainActivity.this
                , Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
                && ContextCompat.checkSelfPermission(MainActivity.this
                , Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == SMS_PERMISSION_CODE) {

            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                BeginMsgGet();

            }

        }

    }

    @Override
    protected void onStart() {
        super.onStart();

    }

    @Override
    protected void onStop() {
        super.onStop();

    }


}