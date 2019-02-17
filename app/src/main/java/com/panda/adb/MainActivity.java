package com.panda.adb;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.TextView;

public class MainActivity extends AppCompatActivity {
    TextView textView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        textView = findViewById(R.id.output);
        textView.setMovementMethod(ScrollingMovementMethod.getInstance());
        final EditText editText = findViewById(R.id.input);
        editText.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    adbExecute(editText.getEditableText().toString());
                    return true;
                }
                return false;
            }
        });
        findViewById(R.id.clear).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                textView.setText("");
            }
        });
    }

    private void adbExecute(String execute) {
        // "shell:dumpsys cpuinfo | grep '" + getPackageName() + "'"
        AdbUtils.executeShell(this,
                "shell:" + execute,
                new AdbUtils.AdbCallback() {
                    @Override
                    public void onSuccess(String adbResponse) {
                        textView.setText(textView.getText() + "\n" + adbResponse);
                    }

                    @Override
                    public void onFail(final String failString) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                textView.setText(textView.getText() + "\n" + failString);
                            }
                        });
                    }
                });
    }
}
