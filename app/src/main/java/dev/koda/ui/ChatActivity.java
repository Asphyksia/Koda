package dev.koda.ui;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.termux.R;
import com.termux.shared.logger.Logger;

import dev.koda.KodaService;

/**
 * Main chat interface.
 * For now: simple monospace log + input.
 * Future: message bubbles, markdown, streaming.
 */
public class ChatActivity extends AppCompatActivity {

    private static final String LOG_TAG = "ChatActivity";

    private TextView mChatLog;
    private ScrollView mChatScroll;
    private EditText mInput;
    private ImageButton mSendButton;

    private KodaService mService;
    private boolean mBound = false;

    private final ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            KodaService.LocalBinder binder = (KodaService.LocalBinder) service;
            mService = binder.getService();
            mBound = true;
            appendLog("🔧 Connected to Koda service\n");
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mBound = false;
            mService = null;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        mChatLog = findViewById(R.id.chat_log);
        mChatScroll = findViewById(R.id.chat_scroll);
        mInput = findViewById(R.id.chat_input);
        mSendButton = findViewById(R.id.send_button);

        mSendButton.setOnClickListener(v -> sendMessage());
        mInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendMessage();
                return true;
            }
            return false;
        });

        appendLog("🐾 Koda — AI Coding Agent\n");
        appendLog("Type a message to start.\n\n");

        Intent intent = new Intent(this, KodaService.class);
        startService(intent);
        bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mBound) {
            unbindService(mConnection);
            mBound = false;
        }
    }

    private void appendLog(String text) {
        mChatLog.append(text);
        mChatScroll.post(() -> mChatScroll.fullScroll(View.FOCUS_DOWN));
    }

    private void sendMessage() {
        String message = mInput.getText().toString().trim();
        if (message.isEmpty()) return;

        mInput.setText("");
        appendLog("\n> " + message + "\n\n");

        if (!mBound || mService == null) {
            appendLog("❌ Service not connected\n");
            return;
        }

        // For now: execute as shell command
        // TODO: replace with gRPC to OpenClaude
        mSendButton.setEnabled(false);
        mService.executeCommand(message, result -> {
            appendLog(result.stdout);
            if (result.exitCode != 0) {
                appendLog("(exit " + result.exitCode + ")\n");
            }
            mSendButton.setEnabled(true);
        });
    }
}
