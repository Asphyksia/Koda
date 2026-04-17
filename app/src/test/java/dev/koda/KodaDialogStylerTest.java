package dev.koda;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.termux.R;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class KodaDialogStylerTest {

    @Test
    public void inlineProgressContent_usesKodaCardStyling() {
        Activity activity = Robolectric.buildActivity(Activity.class)
            .setup()
            .get();

        LinearLayout content = KodaDialogStyler.createInlineProgressContent(
            activity,
            R.string.koda_progress_connecting_channel
        );
        TextView messageView = (TextView) content.getChildAt(1);

        assertNotNull(content.getBackground());
        assertEquals(
            activity.getString(R.string.koda_progress_connecting_channel),
            messageView.getText().toString()
        );
        assertEquals(
            ContextCompat.getColor(activity, R.color.koda_on_background),
            messageView.getCurrentTextColor()
        );
    }

    @Test
    public void applyTransparentCardWindow_setsTransparentBackground() {
        Activity activity = Robolectric.buildActivity(Activity.class)
            .setup()
            .get();

        AlertDialog dialog = new AlertDialog.Builder(activity)
            .setMessage("Test dialog")
            .create();
        dialog.show();

        KodaDialogStyler.applyTransparentCardWindow(dialog);

        Drawable background = dialog.getWindow().getDecorView().getBackground();
        assertTrue(background instanceof ColorDrawable);
        assertEquals(Color.TRANSPARENT, ((ColorDrawable) background).getColor());
    }

    @Test
    public void openclaudeUpdateProgressLayout_usesCardBackground() {
        Activity activity = Robolectric.buildActivity(Activity.class)
            .setup()
            .get();

        View dialogView = LayoutInflater.from(activity).inflate(R.layout.dialog_openclaude_update, null, false);

        assertNotNull(dialogView.getBackground());
    }
}
