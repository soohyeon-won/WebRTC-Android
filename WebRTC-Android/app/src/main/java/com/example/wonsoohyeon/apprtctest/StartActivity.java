package com.example.wonsoohyeon.apprtctest;

import android.content.Context;
import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.Toast;

public class StartActivity extends AppCompatActivity {

    Context mContext = this;
    EditText roomName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_start);
        roomName = findViewById(R.id.room_name);
    }

    public boolean checkRoomName(){
        if(roomName.getText().toString().equals("")){
            return false;
        }
        return true;
    }

    public void buttonListener(View view){
        switch(view.getId()){
            case R.id.start_btn :
                if(checkRoomName() == true) {
                    Intent intent = new Intent(getApplicationContext(), MainActivity.class);
                    intent.putExtra("roomName", roomName.getText().toString());
                    startActivity(intent);
                    finish();
                }
                else{
                    Toast.makeText(getApplicationContext(), "방 이름을 입력하세요.", Toast.LENGTH_LONG).show();
                }
                break;
        }
    }
}
