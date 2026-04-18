package dev.koda.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import com.termux.R;

import dev.koda.data.ChatDatabase;

/**
 * Adapter for conversation list in the drawer.
 */
public class ConversationAdapter extends BaseAdapter {

    private final Context mContext;
    private List<ChatDatabase.Conversation> mConversations = new ArrayList<>();

    public ConversationAdapter(Context context) {
        mContext = context;
    }

    public void setConversations(List<ChatDatabase.Conversation> conversations) {
        mConversations = conversations;
        notifyDataSetChanged();
    }

    @Override public int getCount()                            { return mConversations.size(); }
    @Override public ChatDatabase.Conversation getItem(int p) { return mConversations.get(p); }
    @Override public long getItemId(int p)                     { return mConversations.get(p).id; }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        LinearLayout row;
        if (convertView instanceof LinearLayout) {
            row = (LinearLayout) convertView;
        } else {
            row = new LinearLayout(mContext);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(dp(20), dp(13), dp(16), dp(13));
            // Ripple on tap
            int[] attrs = { android.R.attr.selectableItemBackground };
            android.content.res.TypedArray ta = mContext.obtainStyledAttributes(attrs);
            row.setForeground(ta.getDrawable(0));
            ta.recycle();
        }
        row.removeAllViews();

        ChatDatabase.Conversation conv = mConversations.get(position);

        // ── Title + time row ─────────────────────────────────────
        LinearLayout titleRow = new LinearLayout(mContext);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView title = new TextView(mContext);
        title.setLayoutParams(new LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        String titleText = TextUtils.isEmpty(conv.title) ? "New conversation" : conv.title;
        title.setText(titleText);
        title.setTextColor(ContextCompat.getColor(mContext, R.color.koda_text_primary));
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        title.setTypeface(null, Typeface.NORMAL);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);

        TextView time = new TextView(mContext);
        time.setText(formatTime(conv.updatedAt));
        time.setTextColor(ContextCompat.getColor(mContext, R.color.koda_text_tertiary));
        time.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        time.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT));

        titleRow.addView(title);
        titleRow.addView(time);
        row.addView(titleRow);

        // ── Preview ──────────────────────────────────────────────
        if (!TextUtils.isEmpty(conv.lastMessage)) {
            TextView preview = new TextView(mContext);
            String previewText = conv.lastMessage.replace("\n", " ").trim();
            if (previewText.length() > 80) previewText = previewText.substring(0, 80) + "…";
            preview.setText(previewText);
            preview.setTextColor(ContextCompat.getColor(mContext, R.color.koda_text_tertiary));
            preview.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            preview.setSingleLine(true);
            preview.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.topMargin = dp(3);
            preview.setLayoutParams(lp);
            row.addView(preview);
        }

        return row;
    }

    private String formatTime(long timestamp) {
        long now = System.currentTimeMillis();
        long diff = now - timestamp;
        if (diff < 60_000)    return "now";
        if (diff < 3600_000)  return (diff / 60_000) + "m";
        if (diff < 86400_000) return (diff / 3600_000) + "h";
        return new SimpleDateFormat("MMM d", Locale.getDefault()).format(new Date(timestamp));
    }

    private int dp(int dp) {
        return (int) (dp * mContext.getResources().getDisplayMetrics().density);
    }
}
