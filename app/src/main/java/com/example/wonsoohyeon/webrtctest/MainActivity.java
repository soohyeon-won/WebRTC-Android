package com.example.wonsoohyeon.webrtctest;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

public class MainActivity extends AppCompatActivity {

    Button btn_room;
    EditText edit_room;

    Button btn_url;
    EditText edit_url;

    String room;
    String url;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        btn_room = findViewById(R.id.btn_room);
        edit_room = findViewById(R.id.edit_room);

        btn_url = findViewById(R.id.btn_url);
        edit_url = findViewById(R.id.edit_url);

    }

    public void getEditText(View view){

        switch (view.getId()){
            case R.id.edit_room :
                room = edit_room.getText().toString();
                break;
            case R.id.edit_url:
                url = edit_url.getText().toString();
                break;
        }
    }

    public void start(View view){

    }
}
