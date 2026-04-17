package dev.koda.ui;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.IBinder;
import android.text.method.LinkMovementMethod;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.termux.R;
import com.termux.shared.logger.Logger;

import dev.koda.KodaService;

/**
 * Main chat interface with streaming support and message bubbles.
 */
public class ChatActivity extends AppCompatActivity {

    private static final String LOG_TAG = "ChatActivity";

    private LinearLayout mChatContainer;
    private ScrollView mChatScroll;
    private EditText mInput;
    private ImageButton mSendButton;
    private TextView mCurrentAssistantBubble;

    private KodaService mService;
    private boolean mBound = false;
    private String mSessionId = null;

    private final ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            KodaService.LocalBinder binder = (KodaService.LocalBinder) service;
            mService = binder.getService();
            mBound = true;
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

        mChatContainer = findViewById(R.id.chat_container);
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

        // Welcome message
        addSystemBubble("🐾 Koda — AI Coding Agent\nPowered by Claude via RelayGPU");

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

    // ========== Message Bubbles ==========

    private TextView createBubble(boolean isUser) {
        TextView bubble = new TextView(this);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        int margin = dpToPx(8);
        int hPad = dpToPx(14);
        int vPad = dpToPx(10);

        if (isUser) {
            params.setMargins(dpToPx(48), margin, margin, margin);
            bubble.setBackgroundColor(Color.parseColor("#1E3A5F")); // dark blue
            bubble.setTextColor(Color.parseColor("#E2E8F0"));
            bubble.setGravity(Gravity.END);
        } else {
            params.setMargins(margin, margin, dpToPx(48), margin);
            bubble.setBackgroundColor(Color.parseColor("#1E293B")); // slate
            bubble.setTextColor(Color.parseColor("#F1F5F9"));
        }

        bubble.setLayoutParams(params);
        bubble.setPadding(hPad, vPad, hPad, vPad);
        bubble.setTextSize(15);
        bubble.setLineSpacing(dpToPx(2), 1f);
        bubble.setMovementMethod(LinkMovementMethod.getInstance());

        return bubble;
    }

    private void addUserBubble(String text) {
        TextView bubble = createBubble(true);
        bubble.setText(text);
        mChatContainer.addView(bubble);
        scrollToBottom();
    }

    private TextView addAssistantBubble() {
        TextView bubble = createBubble(false);
        bubble.setText("");
        mChatContainer.addView(bubble);
        scrollToBottom();
        return bubble;
    }

    private void addSystemBubble(String text) {
        TextView bubble = new TextView(this);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        int margin = dpToPx(8);
        params.setMargins(margin, margin, margin, margin);
        bubble.setLayoutParams(params);
        bubble.setPadding(dpToPx(14), dpToPx(8), dpToPx(14), dpToPx(8));
        bubble.setTextSize(13);
        bubble.setTextColor(Color.parseColor("#94A3B8"));
        bubble.setGravity(Gravity.CENTER);
        bubble.setText(text);
        mChatContainer.addView(bubble);
    }

    private void addErrorBubble(String text) {
        TextView bubble = new TextView(this);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        int margin = dpToPx(8);
        params.setMargins(margin, margin, margin, margin);
        bubble.setLayoutParams(params);
        bubble.setPadding(dpToPx(14), dpToPx(8), dpToPx(14), dpToPx(8));
        bubble.setTextSize(13);
        bubble.setTextColor(Color.parseColor("#F87171"));
        bubble.setText("❌ " + text);
        mChatContainer.addView(bubble);
        scrollToBottom();
    }

    private void scrollToBottom() {
        mChatScroll.post(() -> mChatScroll.fullScroll(View.FOCUS_DOWN));
    }

    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }

    // ========== Send Message ==========

    private void sendMessage() {
        String message = mInput.getText().toString().trim();
        if (message.isEmpty()) return;

        mInput.setText("");
        addUserBubble(message);

        if (!mBound || mService == null) {
            addErrorBubble("Service not connected");
            return;
        }

        mSendButton.setEnabled(false);
        mInput.setEnabled(false);
        mCurrentAssistantBubble = addAssistantBubble();

        mService.sendToOpenClaude(message, mSessionId, new KodaService.StreamCallback() {
            @Override
            public void onToken(String token) {
                if (mCurrentAssistantBubble != null) {
                    mCurrentAssistantBubble.append(token);
                    scrollToBottom();
                }
            }

            @Override
            public void onSessionId(String sessionId) {
                mSessionId = sessionId;
            }

            @Override
            public void onComplete(int exitCode) {
                mCurrentAssistantBubble = null;
                mSendButton.setEnabled(true);
                mInput.setEnabled(true);
                mInput.requestFocus();
            }

            @Override
            public void onError(String error) {
                if (mCurrentAssistantBubble != null &&
                    mCurrentAssistantBubble.getText().length() == 0) {
                    // Remove empty bubble
                    mChatContainer.removeView(mCurrentAssistantBubble);
                }
                addErrorBubble(error);
                mCurrentAssistantBubble = null;
                mSendButton.setEnabled(true);
                mInput.setEnabled(true);
            }
        });
    }
}
