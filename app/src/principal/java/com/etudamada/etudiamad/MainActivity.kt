package com.etudamada.etudiamad;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        TextView textView = new TextView(this);
        textView.setText("Java pur - OK !");
        textView.setTextSize(30);
        setContentView(textView);
    }
}
