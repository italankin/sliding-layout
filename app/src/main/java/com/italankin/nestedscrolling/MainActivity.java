package com.italankin.nestedscrolling;

import android.graphics.Color;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;

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
        OverlayLayout overlayLayout = (OverlayLayout) findViewById(R.id.layout);
        overlayLayout.addOnDragProgressListener(new OverlayLayout.OnDragProgressListener() {
            @Override
            public void onDragProgress(float percent) {
                Log.d(TAG, "onDragProgress: " + percent);
            }
        });
    }

    private int[] getData() {
        int[] result = new int[100];
        Random r = new Random();
        for (int i = 0; i < 100; i++) {
            result[i] = Color.rgb(r.nextInt(256), r.nextInt(255), r.nextInt(255));
        }
        return result;
    }
}
