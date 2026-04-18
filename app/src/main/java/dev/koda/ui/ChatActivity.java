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
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.method.LinkMovementMethod;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.drawerlayout.widget.DrawerLayout;

import com.termux.R;
import com.termux.shared.logger.Logger;

import java.util.List;

import io.noties.markwon.Markwon;
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin;
import io.noties.markwon.ext.tables.TablePlugin;
import io.noties.markwon.syntax.Prism4jThemeDarkula;
import io.noties.markwon.syntax.SyntaxHighlightPlugin;
import io.noties.prism4j.Prism4j;

import dev.koda.KodaService;
import dev.koda.data.ChatDatabase;
import dev.koda.data.ProviderManager;

/**
 * Main chat interface with drawer, persistence, and streaming Markdown.
 */
public class ChatActivity extends AppCompatActivity {

    private static final String LOG_TAG = "ChatActivity";

    // UI
    private DrawerLayout mDrawerLayout;
    private LinearLayout mDrawerPanel;
    private ListView mConversationList;
    private ConversationAdapter mConvAdapter;
    private LinearLayout mChatContainer;
    private ScrollView mChatScroll;
    private EditText mInput;
    private ImageButton mSendButton;
    private View mTypingIndicator;
    private View mScrollFab;
    private View mEmptyState;
    private TextView mChatTitle;

    // Markdown
    private Markwon mMarkwon;
    private StringBuilder mCurrentResponseBuffer;
    private TextView mCurrentAssistantBubble;
    private long mCurrentAssistantMsgId = -1;

    // Data
    private ChatDatabase mDb;
    private long mCurrentConvId = -1;
    private String mSessionId = null;

    // Service
    private KodaService mService;
    private boolean mBound = false;
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
            .usePlugin(CodeBlockPlugin.create(this))
            .build();

        // Init DB
        mDb = ChatDatabase.getInstance(this);

        // Views
        mDrawerLayout = findViewById(R.id.drawer_layout);
        mDrawerPanel = findViewById(R.id.drawer_panel);
        mConversationList = findViewById(R.id.conversation_list);
        mChatContainer = findViewById(R.id.chat_container);
        mChatScroll = findViewById(R.id.chat_scroll);
        mInput = findViewById(R.id.chat_input);
        mSendButton = findViewById(R.id.send_button);
        mTypingIndicator = findViewById(R.id.typing_indicator);
        mChatTitle = findViewById(R.id.chat_title);
        mScrollFab = findViewById(R.id.scroll_fab);
        mEmptyState = findViewById(R.id.empty_state);

        // Scroll FAB: appear when not at bottom, tap to scroll down
        mScrollFab.setOnClickListener(v -> scrollToBottom());
        mChatScroll.getViewTreeObserver().addOnScrollChangedListener(() -> {
            View child = mChatScroll.getChildAt(0);
            if (child == null) return;
            int scrollY = mChatScroll.getScrollY();
            int maxScroll = child.getHeight() - mChatScroll.getHeight();
            boolean atBottom = scrollY >= maxScroll - dp(80);
            mScrollFab.setVisibility(atBottom ? View.GONE : View.VISIBLE);
        });

        // Drawer adapter
        mConvAdapter = new ConversationAdapter(this);
        mConversationList.setAdapter(mConvAdapter);
        mConversationList.setOnItemClickListener((parent, view, position, id) -> {
            ChatDatabase.Conversation conv = mConvAdapter.getItem(position);
            loadConversation(conv.id);
            mDrawerLayout.closeDrawers();
        });

        // Long press to delete conversation
        mConversationList.setOnItemLongClickListener((parent, view, position, id) -> {
            ChatDatabase.Conversation conv = mConvAdapter.getItem(position);
            deleteConversation(conv.id);
            return true;
        });

        // Menu button → open drawer
        findViewById(R.id.menu_button).setOnClickListener(v -> {
            refreshConversationList();
            mDrawerLayout.openDrawer(mDrawerPanel);
        });

        // New chat buttons (top bar + drawer)
        View.OnClickListener newChatClick = v -> {
            startNewConversation();
            mDrawerLayout.closeDrawers();
        };
        findViewById(R.id.new_chat_button).setOnClickListener(newChatClick);
        findViewById(R.id.drawer_new_chat).setOnClickListener(newChatClick);

        // Send
        mSendButton.setOnClickListener(v -> sendMessage());
        mSendButton.setEnabled(false);
        mSendButton.setAlpha(0.3f);

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

        mInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendMessage();
                return true;
            }
            return false;
        });

        // Settings button in drawer
        View settingsBtn = findViewById(R.id.drawer_settings);
        View settingsLabel = findViewById(R.id.drawer_settings_label);
        View.OnClickListener settingsClick = v -> {
            mDrawerLayout.closeDrawers();
            startActivity(new Intent(this, SettingsActivity.class));
        };
        settingsBtn.setOnClickListener(settingsClick);
        settingsLabel.setOnClickListener(settingsClick);

        // Bind service
        Intent intent = new Intent(this, KodaService.class);
        startService(intent);
        bindService(intent, mConnection, Context.BIND_AUTO_CREATE);

        // Load most recent conversation or start new
        List<ChatDatabase.Conversation> convs = mDb.getConversations();
        if (!convs.isEmpty()) {
            loadConversation(convs.get(0).id);
        } else {
            startNewConversation();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateModelChip();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mBound) {
            unbindService(mConnection);
            mBound = false;
        }
    }

    // ========== Conversation Management ==========

    private void startNewConversation() {
        mCurrentConvId = mDb.createConversation("", "");
        mSessionId = null;
        mChatContainer.removeAllViews();
        mChatTitle.setText("Koda");
        // Show empty state instead of system bubble
        if (mEmptyState != null) mEmptyState.setVisibility(View.VISIBLE);
    }

    private void loadConversation(long convId) {
        mCurrentConvId = convId;
        mChatContainer.removeAllViews();

        ChatDatabase.Conversation conv = mDb.getConversation(convId);
        if (conv == null) {
            startNewConversation();
            return;
        }

        mSessionId = conv.sessionId;
        String title = (conv.title == null || conv.title.isEmpty()) ? "Koda" : conv.title;
        mChatTitle.setText(title);

        // Reload messages
        List<ChatDatabase.Message> messages = mDb.getMessages(convId);

        // Toggle empty state
        if (mEmptyState != null) {
            mEmptyState.setVisibility(messages.isEmpty() ? View.VISIBLE : View.GONE);
        }
        for (ChatDatabase.Message msg : messages) {
            switch (msg.role) {
                case "user":
                    addUserBubble(msg.content, false);
                    break;
                case "assistant":
                    TextView bubble = addAssistantBubble(false);
                    renderMarkdown(bubble, msg.content);
                    final String rawText = msg.content;
                    bubble.setOnLongClickListener(v -> {
                        copyToClipboard(rawText);
                        return true;
                    });
                    break;
                case "error":
                    addErrorBubble(msg.content);
                    break;
                case "system":
                    addSystemBubble(msg.content);
                    break;
            }
        }
        scrollToBottom();
    }

    private void deleteConversation(long convId) {
        mDb.deleteConversation(convId);
        refreshConversationList();
        if (convId == mCurrentConvId) {
            List<ChatDatabase.Conversation> convs = mDb.getConversations();
            if (!convs.isEmpty()) {
                loadConversation(convs.get(0).id);
            } else {
                startNewConversation();
            }
        }
        Toast.makeText(this, "Deleted", Toast.LENGTH_SHORT).show();
    }

    private void refreshConversationList() {
        mConvAdapter.setConversations(mDb.getConversations());
    }

    /**
     * Auto-generate title from first user message.
     */
    private void autoTitle(String firstMessage) {
        String title = firstMessage.trim();
        if (title.length() > 50) {
            title = title.substring(0, 47) + "...";
        }
        mDb.updateConversationTitle(mCurrentConvId, title);
        mChatTitle.setText(title);
    }

    // ========== Bubble Creation ==========

    private GradientDrawable makeBubbleBackground(String color, float[] radii) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor(color));
        bg.setCornerRadii(radii);
        return bg;
    }

    private TextView createUserBubble(String text) {
        TextView bubble = new TextView(this);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT);
        int m = dp(8);
        params.setMargins(dp(48), m, m, m);
        bubble.setLayoutParams(params);

        float r = dp(16); float s = dp(4);
        bubble.setBackground(makeBubbleBackground("#1E3A5F",
            new float[]{ r, r, r, r, s, s, r, r }));

        bubble.setPadding(dp(14), dp(10), dp(14), dp(10));
        bubble.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        bubble.setTextColor(Color.parseColor("#E2E8F0"));
        bubble.setLineSpacing(dp(3), 1f);
        bubble.setText(text);
        bubble.setOnLongClickListener(v -> { copyToClipboard(text); return true; });
        return bubble;
    }

    private TextView createAssistantBubble() {
        TextView bubble = new TextView(this);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT);
        int m = dp(8);
        params.setMargins(m, m, dp(32), m);
        bubble.setLayoutParams(params);

        float r = dp(16); float s = dp(4);
        bubble.setBackground(makeBubbleBackground("#1E293B",
            new float[]{ r, r, r, r, r, r, s, s }));

        bubble.setPadding(dp(14), dp(10), dp(14), dp(10));
        bubble.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        bubble.setTextColor(Color.parseColor("#F1F5F9"));
        bubble.setLineSpacing(dp(3), 1f);
        bubble.setMovementMethod(LinkMovementMethod.getInstance());
        return bubble;
    }

    private void addUserBubble(String text, boolean animate) {
        TextView bubble = createUserBubble(text);
        mChatContainer.addView(bubble);
        if (animate) {
            bubble.setTranslationY(dp(20));
            bubble.setAlpha(0f);
            bubble.animate().translationY(0).alpha(1f).setDuration(200).start();
        }
        scrollToBottom();
    }

    private TextView addAssistantBubble(boolean animate) {
        TextView bubble = createAssistantBubble();
        mChatContainer.addView(bubble);
        if (animate) {
            bubble.setTranslationX(-dp(20));
            bubble.setAlpha(0f);
            bubble.animate().translationX(0).alpha(1f).setDuration(200).start();
        }
        scrollToBottom();
        return bubble;
    }

    private void addSystemBubble(String text) {
        TextView bubble = new TextView(this);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT);
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
            LinearLayout.LayoutParams.WRAP_CONTENT);
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
            if (tag instanceof ObjectAnimator) ((ObjectAnimator) tag).cancel();
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

    private void renderMarkdown(TextView view, String markdown) {
        mMarkwon.setMarkdown(view, markdown);
    }

    private void updateModelChip() {
        TextView chip = findViewById(R.id.model_label);
        if (chip != null) {
            ProviderManager pm = new ProviderManager(this);
            ProviderManager.Provider active = pm.getActiveProvider();
            if (active != null) {
                // Show short model name
                String model = active.defaultModel;
                if (model.contains("/")) model = model.substring(model.lastIndexOf("/") + 1);
                // Abbreviate common names
                model = model.replace("claude-", "").replace("anthropic-", "");
                chip.setText(active.name + " · " + model);
            }
        }
    }

    // ========== Send ==========

    private void sendMessage() {
        String message = mInput.getText().toString().trim();
        if (message.isEmpty()) return;

        mInput.setText("");

        // Haptic feedback
        try {
            Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (vibrator != null && vibrator.hasVibrator()) {
                vibrator.vibrate(VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE));
            }
        } catch (Exception ignored) {}

        // Hide empty state
        if (mEmptyState != null) mEmptyState.setVisibility(View.GONE);

        // Ensure we have a conversation
        if (mCurrentConvId < 0) {
            mCurrentConvId = mDb.createConversation("", "");
        }

        // Auto-title on first message
        List<ChatDatabase.Message> existing = mDb.getMessages(mCurrentConvId);
        if (existing.isEmpty()) {
            autoTitle(message);
        }

        // Save & display user message
        mDb.addMessage(mCurrentConvId, "user", message);
        addUserBubble(message, true);

        if (!mBound || mService == null) {
            mDb.addMessage(mCurrentConvId, "error", "Service not connected");
            addErrorBubble("Service not connected");
            return;
        }

        setInputEnabled(false);
        showTypingIndicator();
        mCurrentResponseBuffer = new StringBuilder();

        // Get system prompt for this conversation
        String systemPrompt = null;
        ChatDatabase.Conversation conv = mDb.getConversation(mCurrentConvId);
        if (conv != null && conv.systemPrompt != null && !conv.systemPrompt.isEmpty()) {
            systemPrompt = conv.systemPrompt;
        }

        mService.sendToOpenClaude(message, mSessionId, systemPrompt, new KodaService.StreamCallback() {
            @Override
            public void onToken(String token) {
                if (mCurrentAssistantBubble == null) {
                    hideTypingIndicator();
                    mCurrentAssistantBubble = addAssistantBubble(true);
                    // Create DB row for assistant message
                    mCurrentAssistantMsgId = mDb.addMessage(mCurrentConvId, "assistant", "");
                }
                mCurrentResponseBuffer.append(token);
                renderMarkdown(mCurrentAssistantBubble, mCurrentResponseBuffer.toString());
                scrollToBottom();
            }

            @Override
            public void onSessionId(String sessionId) {
                mSessionId = sessionId;
                mDb.updateConversationSessionId(mCurrentConvId, sessionId);
            }

            @Override
            public void onUsage(int inputTokens, int outputTokens, double costUsd) {
                // Token usage tracked but not shown per-message (UI noise).
                // TODO: accumulate and show in profile/stats screen later.
            }

            @Override
            public void onComplete(int exitCode) {
                hideTypingIndicator();
                if (mCurrentAssistantBubble != null && mCurrentResponseBuffer.length() > 0) {
                    final String rawText = mCurrentResponseBuffer.toString();
                    renderMarkdown(mCurrentAssistantBubble, rawText);
                    mCurrentAssistantBubble.setOnLongClickListener(v -> {
                        copyToClipboard(rawText);
                        return true;
                    });
                    // Update DB with final content
                    if (mCurrentAssistantMsgId > 0) {
                        mDb.updateMessageContent(mCurrentAssistantMsgId, rawText);
                    }
                } else if (mCurrentAssistantBubble == null && exitCode != 0) {
                    String err = "Process exited with code " + exitCode;
                    mDb.addMessage(mCurrentConvId, "error", err);
                    addErrorBubble(err);
                }
                mCurrentAssistantBubble = null;
                mCurrentResponseBuffer = null;
                mCurrentAssistantMsgId = -1;
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
                mDb.addMessage(mCurrentConvId, "error", error);
                addErrorBubble(error);
                mCurrentAssistantBubble = null;
                mCurrentResponseBuffer = null;
                mCurrentAssistantMsgId = -1;
                setInputEnabled(true);
            }
        });
    }
}
