package dev.koda.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
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

import dev.koda.R;
import java.util.Locale;

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

    @Override
    public int getCount() {
        return mConversations.size();
    }

    @Override
    public ChatDatabase.Conversation getItem(int position) {
        return mConversations.get(position);
    }

    @Override
    public long getItemId(int position) {
        return mConversations.get(position).id;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        LinearLayout row;
        if (convertView instanceof LinearLayout) {
            row = (LinearLayout) convertView;
        } else {
            row = new LinearLayout(mContext);
            row.setOrientation(LinearLayout.VERTICAL);
            int pad = dp(14);
            row.setPadding(pad, dp(12), pad, dp(12));
        }
        row.removeAllViews();

        ChatDatabase.Conversation conv = mConversations.get(position);

        // Title row (title + time)
        LinearLayout titleRow = new LinearLayout(mContext);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);

        TextView title = new TextView(mContext);
        title.setLayoutParams(new LinearLayout.LayoutParams(0,
            LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        String titleText = TextUtils.isEmpty(conv.title) ? "New conversation" : conv.title;
        title.setText(titleText);
        title.setTextColor(ContextCompat.getColor(mContext, R.color.koda_text_primary));
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        title.setTypeface(null, Typeface.BOLD);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);

        TextView time = new TextView(mContext);
        time.setText(formatTime(conv.updatedAt));
        time.setTextColor(ContextCompat.getColor(mContext, R.color.koda_text_tertiary));
        time.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);

        titleRow.addView(title);
        titleRow.addView(time);
        row.addView(titleRow);

        // Preview
        if (!TextUtils.isEmpty(conv.lastMessage)) {
            TextView preview = new TextView(mContext);
            String previewText = conv.lastMessage.replace("\n", " ").trim();
            if (previewText.length() > 80) previewText = previewText.substring(0, 80) + "…";
            preview.setText(previewText);
            preview.setTextColor(ContextCompat.getColor(mContext, R.color.koda_text_secondary));
            preview.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            preview.setSingleLine(true);
            preview.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.topMargin = dp(2);
            preview.setLayoutParams(lp);
            row.addView(preview);
        }

        return row;
    }

    private String formatTime(long timestamp) {
        long now = System.currentTimeMillis();
        long diff = now - timestamp;

        if (diff < 60_000) return "now";
        if (diff < 3600_000) return (diff / 60_000) + "m";
        if (diff < 86400_000) return (diff / 3600_000) + "h";

        SimpleDateFormat sdf = new SimpleDateFormat("MMM d", Locale.getDefault());
        return sdf.format(new Date(timestamp));
    }

    private int dp(int dp) {
        return (int) (dp * mContext.getResources().getDisplayMetrics().density);
    }
}
