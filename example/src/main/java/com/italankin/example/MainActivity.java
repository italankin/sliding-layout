package com.italankin.example;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.italankin.slidinglayout.SlidingLayout;

import java.util.Random;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        RecyclerView recyclerView = (RecyclerView) findViewById(R.id.list);
        LinearLayoutManager layoutManager = new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false);
        recyclerView.setLayoutManager(layoutManager);
        final int[] data = getData();
        ListAdapter adapter = new ListAdapter(this, data);
        recyclerView.setAdapter(adapter);
        SlidingLayout slidingLayout = (SlidingLayout) findViewById(R.id.layout);
        slidingLayout.addOnDragProgressListener(new SlidingLayout.OnDragProgressListener() {
            @Override
            public void onDragProgress(float percent) {
                Log.d(TAG, "onDragProgress: " + percent);
            }
        });
        Button button = (Button) findViewById(R.id.button);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(MainActivity.this, "Button clicked!", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private int[] getData() {
        int[] result = new int[100];
        Random r = new Random();
        for (int i = 0; i < 100; i++) {
            result[i] = 0xff000000 | r.nextInt();
        }
        return result;
    }
}
