package com.rozmus.terry.subtitler;

import android.app.Activity;
import android.app.ListActivity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.text.Html;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.GridView;
import android.provider.MediaStore;
import android.database.Cursor;
import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;


public class ChooseSubtitles extends AppCompatActivity {

    boolean firstTime = true;
    ListView subtitleFileView;
    static private List<String> subtitleNames;
    static private List<String> subtitleUrls;

    private void getSubtitleFiles() {
        subtitleNames = new ArrayList<String>();
        subtitleUrls = new ArrayList<String>();
        String[] columns = { MediaStore.Files.FileColumns.DATA };
        String selection = MediaStore.Files.FileColumns.DATA + " like ?";
        String[] arguments = {"%.srt"};
        String order = null;

        Uri path = MediaStore.Files.getContentUri("external");
        Cursor cursor = getContentResolver().query(path, columns, selection, arguments, order);
        try {
            while (cursor.moveToNext()) {
                String uri = cursor.getString(0);

                // Find the name in full path to file
                int nameStart = uri.lastIndexOf('/') + 1;
                int nameFinish = uri.lastIndexOf('.');

                subtitleNames.add(uri.substring(nameStart, nameFinish));
                subtitleUrls.add(uri);
            }
        } finally {
            cursor.close();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Make sure the action bar is hidden
        getSupportActionBar().hide();

        setContentView(R.layout.activity_choose_subtitles);
    }

    @Override
    protected void onStart() {
        super.onStart();

        // Do a first load or reload when returning from an activity
        // outside the Image Viewer app
        if (firstTime) {
            if (Build.VERSION.SDK_INT > 23 && checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 1);
            } else {
                getSubtitleFiles();
            }
            firstTime = false;

            // Populate listview with subtitle files
            subtitleFileView = findViewById(R.id.subtitle_file_list);
            ArrayAdapter<String> subtitleListAdapter = new ArrayAdapter<String>(this,
                    android.R.layout.simple_list_item_1, subtitleNames){
                @Override
                public View getView(int position, View convertView, ViewGroup parent) {
                    // Set the ListView item properties
                    View view = super.getView(position, convertView, parent);
                    TextView v = (TextView) view.findViewById(android.R.id.text1);
                    v.setText(Html.fromHtml(subtitleNames.get(position)));
                    v.setPadding(25, 25, 25, 25);
                    v.setGravity(Gravity.CENTER_HORIZONTAL);
                    v.setTextColor(Color.WHITE);
                    v.setTextSize(25);
                    return view;
                }
            };
            subtitleFileView.setAdapter(subtitleListAdapter);

            subtitleFileView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
                    // Pass uri of selected subtitle file back to main activity
                    Intent returningIntent = new Intent();
                    returningIntent.putExtra("subtitle_uri", subtitleUrls.get(position));
                    setResult(Activity.RESULT_OK, returningIntent);
                    finish();
                }
            });
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        firstTime = true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        if(grantResults.length > 0
                && grantResults[0] != PackageManager.PERMISSION_GRANTED)
            finish();
        else
            getSubtitleFiles();
    }
}

