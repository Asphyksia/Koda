package dev.koda.ui;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.IBinder;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.method.LinkMovementMethod;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.termux.R;
import com.termux.shared.logger.Logger;

import io.noties.markwon.Markwon;
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin;
import io.noties.markwon.ext.tables.TablePlugin;
import io.noties.markwon.syntax.Prism4jThemeDarkula;
import io.noties.markwon.syntax.SyntaxHighlightPlugin;
import io.noties.prism4j.Prism4j;

import dev.koda.KodaService;

/**
 * Main chat interface with streaming Markdown rendering.
 */
public class ChatActivity extends AppCompatActivity {

    private static final String LOG_TAG = "ChatActivity";

    private LinearLayout mChatContainer;
    private ScrollView mChatScroll;
    private EditText mInput;
    private ImageButton mSendButton;
    private View mTypingIndicator;

    // Markdown
    private Markwon mMarkwon;
    private StringBuilder mCurrentResponseBuffer;
    private TextView mCurrentAssistantBubble;

    // Service
    private KodaService mService;
    private boolean mBound = false;
    private String mSessionId = null;
    private boolean mIsGenerating = false;

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

        // Init Markwon
        Prism4j prism4j = new Prism4j(new KodaGrammarLocator());
        mMarkwon = Markwon.builder(this)
            .usePlugin(StrikethroughPlugin.create())
            .usePlugin(TablePlugin.create(this))
            .usePlugin(SyntaxHighlightPlugin.create(prism4j, Prism4jThemeDarkula.create()))
            .build();

        mChatContainer = findViewById(R.id.chat_container);
        mChatScroll = findViewById(R.id.chat_scroll);
        mInput = findViewById(R.id.chat_input);
        mSendButton = findViewById(R.id.send_button);
        mTypingIndicator = findViewById(R.id.typing_indicator);

        // Send button
        mSendButton.setOnClickListener(v -> sendMessage());
        mSendButton.setEnabled(false);
        mSendButton.setAlpha(0.3f);

        // Input: watch for text changes to toggle send button
        mInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                boolean hasText = s.toString().trim().length() > 0;
                mSendButton.setEnabled(hasText && !mIsGenerating);
                mSendButton.setAlpha(hasText && !mIsGenerating ? 1.0f : 0.3f);
            }
        });

        // Enter to send (with shift+enter for newline)
        mInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendMessage();
                return true;
            }
            return false;
        });

        // Welcome
        addSystemBubble("🐾 Koda v0.2\nAI Coding Agent — powered by Claude");

        // Bind service
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

    // ========== Bubble Creation ==========

    private GradientDrawable makeBubbleBackground(String color, float[] radii) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor(color));
        bg.setCornerRadii(radii); // [tl, tl, tr, tr, br, br, bl, bl]
        return bg;
    }

    private TextView createUserBubble(String text) {
        TextView bubble = new TextView(this);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        int m = dp(8);
        params.setMargins(dp(48), m, m, m);
        bubble.setLayoutParams(params);

        float r = dp(16);
        float s = dp(4);
        bubble.setBackground(makeBubbleBackground("#1E3A5F",
            new float[]{ r, r, r, r, s, s, r, r }));  // small bottom-right

        bubble.setPadding(dp(14), dp(10), dp(14), dp(10));
        bubble.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        bubble.setTextColor(Color.parseColor("#E2E8F0"));
        bubble.setLineSpacing(dp(3), 1f);
        bubble.setText(text);

        // Long press to copy
        bubble.setOnLongClickListener(v -> {
            copyToClipboard(text);
            return true;
        });

        return bubble;
    }

    private TextView createAssistantBubble() {
        TextView bubble = new TextView(this);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        int m = dp(8);
        params.setMargins(m, m, dp(32), m);
        bubble.setLayoutParams(params);

        float r = dp(16);
        float s = dp(4);
        bubble.setBackground(makeBubbleBackground("#1E293B",
            new float[]{ r, r, r, r, r, r, s, s }));  // small bottom-left

        bubble.setPadding(dp(14), dp(10), dp(14), dp(10));
        bubble.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        bubble.setTextColor(Color.parseColor("#F1F5F9"));
        bubble.setLineSpacing(dp(3), 1f);
        bubble.setMovementMethod(LinkMovementMethod.getInstance());

        // Long press to copy raw markdown
        bubble.setOnLongClickListener(v -> {
            if (mCurrentResponseBuffer != null) {
                copyToClipboard(mCurrentResponseBuffer.toString());
            } else {
                copyToClipboard(bubble.getText().toString());
            }
            return true;
        });

        return bubble;
    }

    private void addUserBubble(String text) {
        TextView bubble = createUserBubble(text);
        mChatContainer.addView(bubble);

        // Slide-in animation
        bubble.setTranslationY(dp(20));
        bubble.setAlpha(0f);
        bubble.animate().translationY(0).alpha(1f).setDuration(200).start();

        scrollToBottom();
    }

    private TextView addAssistantBubble() {
        TextView bubble = createAssistantBubble();
        mChatContainer.addView(bubble);

        bubble.setTranslationX(-dp(20));
        bubble.setAlpha(0f);
        bubble.animate().translationX(0).alpha(1f).setDuration(200).start();

        scrollToBottom();
        return bubble;
    }

    private void addSystemBubble(String text) {
        TextView bubble = new TextView(this);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        int m = dp(8);
        params.setMargins(dp(24), dp(16), dp(24), dp(16));
        bubble.setLayoutParams(params);
        bubble.setPadding(dp(14), dp(10), dp(14), dp(10));
        bubble.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        bubble.setTextColor(Color.parseColor("#64748B"));
        bubble.setGravity(Gravity.CENTER);
        bubble.setTypeface(null, Typeface.ITALIC);
        bubble.setText(text);
        mChatContainer.addView(bubble);
    }

    private void addErrorBubble(String text) {
        TextView bubble = new TextView(this);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        int m = dp(8);
        params.setMargins(m, m, m, m);
        bubble.setLayoutParams(params);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#1C1017"));
        bg.setCornerRadius(dp(12));
        bg.setStroke(dp(1), Color.parseColor("#7F1D1D"));
        bubble.setBackground(bg);

        bubble.setPadding(dp(14), dp(10), dp(14), dp(10));
        bubble.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        bubble.setTextColor(Color.parseColor("#FCA5A5"));
        bubble.setText("⚠ " + text);
        mChatContainer.addView(bubble);
        scrollToBottom();
    }

    // ========== Typing Indicator ==========

    private void showTypingIndicator() {
        if (mTypingIndicator != null) {
            mTypingIndicator.setVisibility(View.VISIBLE);
            // Pulse animation
            ObjectAnimator pulse = ObjectAnimator.ofFloat(mTypingIndicator, "alpha", 0.3f, 1f);
            pulse.setDuration(600);
            pulse.setRepeatCount(ValueAnimator.INFINITE);
            pulse.setRepeatMode(ValueAnimator.REVERSE);
            pulse.setInterpolator(new AccelerateDecelerateInterpolator());
            pulse.start();
            mTypingIndicator.setTag(pulse);
            scrollToBottom();
        }
    }

    private void hideTypingIndicator() {
        if (mTypingIndicator != null) {
            Object tag = mTypingIndicator.getTag();
            if (tag instanceof ObjectAnimator) {
                ((ObjectAnimator) tag).cancel();
            }
            mTypingIndicator.setVisibility(View.GONE);
            mTypingIndicator.setAlpha(1f);
        }
    }

    // ========== Helpers ==========

    private void scrollToBottom() {
        mChatScroll.post(() -> mChatScroll.fullScroll(View.FOCUS_DOWN));
    }

    private int dp(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }

    private void copyToClipboard(String text) {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("Koda", text));
        Toast.makeText(this, "Copied", Toast.LENGTH_SHORT).show();
    }

    private void setInputEnabled(boolean enabled) {
        mIsGenerating = !enabled;
        mInput.setEnabled(enabled);
        boolean hasText = mInput.getText().toString().trim().length() > 0;
        mSendButton.setEnabled(enabled && hasText);
        mSendButton.setAlpha(enabled && hasText ? 1.0f : 0.3f);
        if (enabled) mInput.requestFocus();
    }

    // ========== Render Markdown ==========

    private void renderMarkdown(TextView view, String markdown) {
        mMarkwon.setMarkdown(view, markdown);
    }

    // ========== Send ==========

    private void sendMessage() {
        String message = mInput.getText().toString().trim();
        if (message.isEmpty()) return;

        mInput.setText("");
        addUserBubble(message);

        if (!mBound || mService == null) {
            addErrorBubble("Service not connected");
            return;
        }

        setInputEnabled(false);
        showTypingIndicator();
        mCurrentResponseBuffer = new StringBuilder();

        mService.sendToOpenClaude(message, mSessionId, new KodaService.StreamCallback() {
            @Override
            public void onToken(String token) {
                // First token — hide typing, create bubble
                if (mCurrentAssistantBubble == null) {
                    hideTypingIndicator();
                    mCurrentAssistantBubble = addAssistantBubble();
                }
                mCurrentResponseBuffer.append(token);
                // Re-render markdown on each token
                renderMarkdown(mCurrentAssistantBubble, mCurrentResponseBuffer.toString());
                scrollToBottom();
            }

            @Override
            public void onSessionId(String sessionId) {
                mSessionId = sessionId;
            }

            @Override
            public void onComplete(int exitCode) {
                hideTypingIndicator();
                // Final render
                if (mCurrentAssistantBubble != null && mCurrentResponseBuffer.length() > 0) {
                    renderMarkdown(mCurrentAssistantBubble, mCurrentResponseBuffer.toString());

                    // Store the raw text for copy
                    final String rawText = mCurrentResponseBuffer.toString();
                    mCurrentAssistantBubble.setOnLongClickListener(v -> {
                        copyToClipboard(rawText);
                        return true;
                    });
                } else if (mCurrentAssistantBubble == null && exitCode != 0) {
                    hideTypingIndicator();
                    addErrorBubble("Process exited with code " + exitCode);
                }
                mCurrentAssistantBubble = null;
                mCurrentResponseBuffer = null;
                setInputEnabled(true);
            }

            @Override
            public void onError(String error) {
                hideTypingIndicator();
                if (mCurrentAssistantBubble != null &&
                    mCurrentResponseBuffer != null &&
                    mCurrentResponseBuffer.length() == 0) {
                    mChatContainer.removeView(mCurrentAssistantBubble);
                }
                addErrorBubble(error);
                mCurrentAssistantBubble = null;
                mCurrentResponseBuffer = null;
                setInputEnabled(true);
            }
        });
    }
}
