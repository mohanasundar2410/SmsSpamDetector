package com.example.smsspamdetector;

import android.app.PendingIntent;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.os.AsyncTask;
import android.os.Bundle;
import android.telephony.SmsManager;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.smsspamdetector.Adapter.MessageDetailsListAdapter;
import com.example.smsspamdetector.Utils.CheckSpamUtils;
import com.example.smsspamdetector.Utils.MsgData;

import org.tensorflow.lite.support.label.Category;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class MsgDetailsActivity extends AppCompatActivity {

    private static final String TAG = "MsgDetailExp";
    TextView msgTitle;

    DatabaseManager databaseManager;

    RecyclerView recyclerView;

    ArrayList<MsgData> msgData;

    MessageDetailsListAdapter adapter;

    CheckSpamUtils checkSpamUtils;

    Button report;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_msg_details);

        databaseManager = new DatabaseManager(getApplicationContext());
        checkSpamUtils = new CheckSpamUtils(getApplicationContext(), MsgDetailsActivity.this);
        recyclerView = findViewById(R.id.all_mgs_list);
        msgTitle = findViewById(R.id.detail_msg_title);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        report = findViewById(R.id.report_msg);

        Intent i = getIntent();

        msgTitle.setText(i.getStringExtra("title"));
        String dateMsg = i.getStringExtra("date");

        report.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {


                Intent intent = new Intent(getApplicationContext(), MainActivity.class);
                PendingIntent pi = PendingIntent.getActivity(getApplicationContext(), 0, intent, 0);

                SmsManager sms = SmsManager.getDefault();
                String text = "The Unsolicited Commercial Communication," + msgTitle + ", " + dateMsg;
                sms.sendTextMessage("1909", null, text, pi, null);

            }
        });
        msgData = new ArrayList<>();
        String id = i.getStringExtra("id");
        Boolean isSpam = i.getBooleanExtra("isSpam", true);

        GetMessage getMessage = new GetMessage();
        getMessage.execute(id);
    }

    private class GetMessage extends AsyncTask<String, Integer, Integer> {

        ArrayList<MsgData> msgData;
        boolean spam = true;

        @Override
        protected void onPreExecute() {
            super.onPreExecute();

            msgData = new ArrayList<>();
            adapter = new MessageDetailsListAdapter(MsgDetailsActivity.this, msgData);
            recyclerView.setAdapter(adapter);

        }

        @Override
        protected Integer doInBackground(String... strings) {

            Cursor cursor = databaseManager.getAllAddressMessage(strings[0]);


            if (cursor.moveToFirst()) {

                int messageId = cursor.getColumnIndex(DatabaseManager.M_ID);
                int messageData = cursor.getColumnIndex(DatabaseManager.M_MESSAGE);
                int messageDate = cursor.getColumnIndex(DatabaseManager.M_TIME);
                int spam = cursor.getColumnIndex(DatabaseManager.M_IS_SPAM);

                do {

                    MsgData temp = new MsgData();

                    temp.setId(cursor.getString(messageId));
                    temp.setMsgBody(cursor.getString(messageData));
                    temp.setMsgDateLong(cursor.getLong(messageDate));
                    temp.setSpam(cursor.getInt(spam) != 0);

                    DateFormat dateFormat = new SimpleDateFormat("dd MMM yyyy HH:mm:ss");
                    Date date = new Date(cursor.getLong(messageDate));

                    temp.setMsgDate(dateFormat.format(date));


                    msgData.add(temp);
                    publishProgress(0);
                } while (cursor.moveToNext());
            }

            cursor.moveToFirst();

            checkSpamMessage(cursor, strings[0]);

            /*if (!cursor.isClosed())
                cursor.close();*/

            return 0;
        }

        private int checkSpamMessage(Cursor cursor, String id) {
            int index_checked = cursor.getColumnIndex(DatabaseManager.M_IS_CHECKED);
            int index_message = cursor.getColumnIndex(DatabaseManager.M_MESSAGE);
            int index_id = cursor.getColumnIndex(DatabaseManager.M_ID);

            if (cursor.getInt(index_checked) == 0) {
                ByteBuffer buffer = StandardCharsets.UTF_8.encode(cursor.getString(index_message));

                String utf8EncodedString = StandardCharsets.UTF_8.decode(buffer).toString();

                checkSpamUtils.classify(utf8EncodedString, new CheckSpamUtils.OnSpamGotListener() {
                    @Override
                    public void onResult(List<Category> results) {

                        Log.e(TAG, "isSpamMessage: msg : \n 0 : " + results.get(0).getLabel() + " : " + results.get(0).getScore() + "\n");
                        Log.e(TAG, "isSpamMessage: 1 : " + results.get(1).getLabel() + " : " + results.get(1).getScore() + "\n");

                        if (results.get(1).getScore() > results.get(0).getScore()) {
                            databaseManager.UpdateSpamChecked(cursor.getInt(index_id), 1, 1, 0);
                            databaseManager.UpdateSpamChecked(Integer.parseInt(id), 1, 1, 1);
                            if(spam)
                            ShowAlert();
                            spam = false;
                            report.setVisibility(View.VISIBLE);
                        } else {
                            databaseManager.UpdateSpamChecked(cursor.getInt(index_id), 0, 1, 0);
                        }
                        if (cursor.moveToNext())
                            checkSpamMessage(cursor, id);
                        else
                            return;
                    }
                });

            } else {
                if (cursor.moveToNext()) {
                    checkSpamMessage(cursor, id);
                }
            }
            return 0;
        }

        private void ShowAlert() {

            AlertDialog.Builder builder = new AlertDialog.Builder(MsgDetailsActivity.this);
            builder.setTitle("Spam Detected");
            builder.setMessage("We Detected spam message in this address please report it to cyber crime");
            builder.setPositiveButton("ok", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                    dialogInterface.dismiss();
                }
            });
            builder.show();

        }

        @Override
        protected void onProgressUpdate(Integer... values) {
            super.onProgressUpdate(values);
            adapter.notifyDataSetChanged();
        }

        @Override
        protected void onPostExecute(Integer integer) {
            super.onPostExecute(integer);
            adapter.notifyDataSetChanged();
        }
    }
}