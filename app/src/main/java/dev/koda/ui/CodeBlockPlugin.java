package dev.koda.ui;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.Layout;
import android.text.Spanned;
import android.text.style.ClickableSpan;
import android.text.style.LeadingMarginSpan;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;

import org.commonmark.node.FencedCodeBlock;
import org.commonmark.node.IndentedCodeBlock;

import io.noties.markwon.AbstractMarkwonPlugin;
import io.noties.markwon.MarkwonSpansFactory;
import io.noties.markwon.SpanFactory;
import io.noties.markwon.MarkwonConfiguration;

/**
 * Markwon plugin that styles code blocks with a dark background
 * and rounded corners. Also makes them long-press-copyable.
 */
public class CodeBlockPlugin extends AbstractMarkwonPlugin {

    private final Context mContext;

    public CodeBlockPlugin(Context context) {
        mContext = context;
    }

    public static CodeBlockPlugin create(Context context) {
        return new CodeBlockPlugin(context);
    }

    @Override
    public void configureSpansFactory(@NonNull MarkwonSpansFactory.Builder builder) {
        // Fenced code blocks (```code```)
        SpanFactory fencedFactory = builder.getFactory(FencedCodeBlock.class);
        builder.setFactory(FencedCodeBlock.class, (configuration, props) -> {
            Object[] original = fencedFactory != null ?
                toArray(fencedFactory.getSpans(configuration, props)) : new Object[0];

            Object[] combined = new Object[original.length + 1];
            System.arraycopy(original, 0, combined, 0, original.length);
            combined[original.length] = new CodeBlockBackgroundSpan();
            return combined;
        });

        // Indented code blocks
        SpanFactory indentedFactory = builder.getFactory(IndentedCodeBlock.class);
        builder.setFactory(IndentedCodeBlock.class, (configuration, props) -> {
            Object[] original = indentedFactory != null ?
                toArray(indentedFactory.getSpans(configuration, props)) : new Object[0];

            Object[] combined = new Object[original.length + 1];
            System.arraycopy(original, 0, combined, 0, original.length);
            combined[original.length] = new CodeBlockBackgroundSpan();
            return combined;
        });
    }

    private Object[] toArray(Object spans) {
        if (spans == null) return new Object[0];
        if (spans instanceof Object[]) return (Object[]) spans;
        return new Object[]{spans};
    }

    /**
     * Span that draws a rounded dark background behind code blocks.
     */
    public static class CodeBlockBackgroundSpan implements LeadingMarginSpan {

        private static final int BG_COLOR = Color.parseColor("#0F172A");
        private static final int BORDER_COLOR = Color.parseColor("#334155");
        private static final int PADDING = 24;
        private static final int RADIUS = 16;

        @Override
        public int getLeadingMargin(boolean first) {
            return PADDING;
        }

        @Override
        public void drawLeadingMargin(Canvas c, Paint p, int x, int dir,
                                       int top, int baseline, int bottom,
                                       CharSequence text, int start, int end,
                                       boolean first, Layout layout) {
            if (!(text instanceof Spanned)) return;
            Spanned spanned = (Spanned) text;

            int spanStart = spanned.getSpanStart(this);
            int spanEnd = spanned.getSpanEnd(this);

            // Only draw background on the first line of the span
            if (start == spanStart) {
                Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                bgPaint.setColor(BG_COLOR);

                Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                borderPaint.setColor(BORDER_COLOR);
                borderPaint.setStyle(Paint.Style.STROKE);
                borderPaint.setStrokeWidth(2f);

                // Calculate the full block bounds
                int firstLine = layout.getLineForOffset(spanStart);
                int lastLine = layout.getLineForOffset(spanEnd);
                float blockTop = layout.getLineTop(firstLine) - 8;
                float blockBottom = layout.getLineBottom(lastLine) + 8;
                float blockLeft = 0;
                float blockRight = layout.getWidth();

                RectF rect = new RectF(blockLeft, blockTop, blockRight, blockBottom);
                c.drawRoundRect(rect, RADIUS, RADIUS, bgPaint);
                c.drawRoundRect(rect, RADIUS, RADIUS, borderPaint);
            }
        }
    }
}
