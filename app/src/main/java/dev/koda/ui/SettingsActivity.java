package dev.koda.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.termux.R;

import java.util.List;

import dev.koda.data.ProviderManager;

/**
 * Settings screen — programmatic layout using design system tokens.
 */
public class SettingsActivity extends AppCompatActivity {

    private ProviderManager mProviderMgr;
    private LinearLayout mContainer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mProviderMgr = new ProviderManager(this);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(ContextCompat.getColor(this, R.color.koda_bg));
        scroll.setFillViewport(false);

        mContainer = new LinearLayout(this);
        mContainer.setOrientation(LinearLayout.VERTICAL);
        mContainer.setPadding(0, 0, 0, dp(40));
        scroll.addView(mContainer);
        setContentView(scroll);

        buildUI();
    }

    private void buildUI() {
        mContainer.removeAllViews();
        buildTopBar();
        buildProvidersSection();
        buildAboutSection();
    }

    // =========================================================
    // Top bar
    // =========================================================

    private void buildTopBar() {
        // Bar background
        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setBackgroundColor(ContextCompat.getColor(this, R.color.koda_surface_1));
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setPadding(dp(4), 0, dp(16), 0);
        topBar.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(56)));

        // Back button with ripple
        ImageButton back = new ImageButton(this);
        back.setImageResource(android.R.drawable.ic_menu_revert);
        back.setColorFilter(ContextCompat.getColor(this, R.color.koda_text_secondary));
        back.setBackground(rippleBorderless());
        back.setContentDescription("Back");
        back.setOnClickListener(v -> finish());
        back.setLayoutParams(new LinearLayout.LayoutParams(dp(44), dp(44)));
        topBar.addView(back);

        TextView title = new TextView(this);
        title.setText("Settings");
        title.setTextColor(ContextCompat.getColor(this, R.color.koda_text_primary));
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        title.setTypeface(null, Typeface.BOLD);
        title.setPadding(dp(4), 0, 0, 0);
        topBar.addView(title);

        mContainer.addView(topBar);

        // Separator
        View sep = new View(this);
        sep.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(1)));
        sep.setBackgroundColor(ContextCompat.getColor(this, R.color.koda_stroke));
        mContainer.addView(sep);
    }

    // =========================================================
    // Providers section
    // =========================================================

    private void buildProvidersSection() {
        addSectionHeader("LLM Providers");

        List<ProviderManager.Provider> providers = mProviderMgr.getProviders();
        ProviderManager.Provider active = mProviderMgr.getActiveProvider();
        for (ProviderManager.Provider p : providers) {
            addProviderCard(p, active != null && p.id.equals(active.id));
        }

        addGhostButton("+ Add Provider", v -> showAddProviderDialog());
    }

    // =========================================================
    // About section
    // =========================================================

    private void buildAboutSection() {
        addSectionHeader("About");

        LinearLayout group = new LinearLayout(this);
        group.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams groupParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        groupParams.setMargins(dp(16), 0, dp(16), dp(4));
        group.setLayoutParams(groupParams);
        GradientDrawable groupBg = new GradientDrawable();
        groupBg.setColor(ContextCompat.getColor(this, R.color.koda_surface_1));
        groupBg.setCornerRadius(dp(12));
        groupBg.setStroke(dp(1), ContextCompat.getColor(this, R.color.koda_stroke));
        group.setBackground(groupBg);

        addInfoRow(group, "Version",    "0.3.0",           false);
        addInfoRow(group, "Engine",     "OpenClaude 0.4.0", true);
        addInfoRow(group, "Runtime",    "Termux (aarch64)", true);

        mContainer.addView(group);

        addSectionHeader("Maintenance");
        addGhostButton("Re-apply patches", v -> repatchOpenClaude());
    }

    private void repatchOpenClaude() {
        Toast.makeText(this, "Applying patches…", Toast.LENGTH_SHORT).show();
        String prefix = "/data/data/com.termux/files/usr";
        String bin    = prefix + "/bin";
        String bash   = bin + "/bash";
        String script =
            "CLIMJS=\"" + prefix + "/lib/node_modules/@gitlawb/openclaude/dist/cli.mjs\"\n" +
            "if [ ! -f \"$CLIMJS\" ]; then echo 'cli.mjs not found'; exit 1; fi\n" +
            "sed -i 's/metadata: getAPIMetadata()/metadata: undefined/g' \"$CLIMJS\" && " +
            "sed -i 's/os\\.tmpdir()/process.env.TMPDIR||os.tmpdir()/g' \"$CLIMJS\" && " +
            "mkdir -p \"" + prefix + "/tmp\" && " +
            "chmod 700 \"" + prefix + "/tmp\" && " +
            "echo OK";

        new Thread(() -> {
            String[] args = { bash, "-c", script };
            // Minimal env — just enough for bash + sed
            String[] env = {
                "HOME=/data/data/com.termux/files/home",
                "PREFIX=" + prefix,
                "PATH=" + bin,
                "LD_LIBRARY_PATH=" + prefix + "/lib",
                "LD_PRELOAD=" + prefix + "/lib/libtermux-exec.so",
                "TMPDIR=" + prefix + "/tmp",
                "LANG=en_US.UTF-8"
            };
            int[] pidOut = new int[1];
            int fd = dev.koda.KodaProcess.createSubprocessPipe(bash, args, env, pidOut);
            String out = "";
            if (fd >= 0) {
                try (java.io.BufferedReader r = new java.io.BufferedReader(
                        new java.io.InputStreamReader(
                            new java.io.FileInputStream("/proc/self/fd/" + fd)))) {
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = r.readLine()) != null) sb.append(line).append("\n");
                    out = sb.toString().trim();
                } catch (Exception ignored) {}
                dev.koda.KodaProcess.waitFor(pidOut[0]);
                dev.koda.KodaProcess.close(fd);
            }
            final String result = out.isEmpty() ? "No output" : out;
            runOnUiThread(() ->
                Toast.makeText(this,
                    result.equals("OK") ? "Patches applied ✓" : "Result: " + result,
                    Toast.LENGTH_LONG).show());
        }).start();
    }

    // =========================================================
    // Provider card
    // =========================================================

    private void addProviderCard(ProviderManager.Provider provider, boolean isActive) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cardParams.setMargins(dp(16), dp(6), dp(16), dp(6));
        card.setLayoutParams(cardParams);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));

        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(ContextCompat.getColor(this, R.color.koda_surface_1));
        cardBg.setCornerRadius(dp(12));
        if (isActive) {
            cardBg.setStroke(dp(2), ContextCompat.getColor(this, R.color.koda_primary));
        } else {
            cardBg.setStroke(dp(1), ContextCompat.getColor(this, R.color.koda_stroke));
        }
        card.setBackground(cardBg);

        // ── Row 1: name + active badge ─────────────────────────
        LinearLayout row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        row1.setGravity(Gravity.CENTER_VERTICAL);

        TextView name = new TextView(this);
        name.setLayoutParams(new LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        name.setText(provider.name);
        name.setTextColor(ContextCompat.getColor(this, R.color.koda_text_primary));
        name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        name.setTypeface(null, Typeface.BOLD);
        row1.addView(name);

        if (isActive) {
            TextView badge = new TextView(this);
            badge.setText("Active");
            badge.setTextColor(ContextCompat.getColor(this, R.color.koda_primary));
            badge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            badge.setTypeface(null, Typeface.BOLD);
            badge.setPadding(dp(8), dp(3), dp(8), dp(3));
            badge.setBackground(ContextCompat.getDrawable(this, R.drawable.badge_active));
            row1.addView(badge);
        }
        card.addView(row1);

        // ── Row 2: model ───────────────────────────────────────
        TextView model = new TextView(this);
        model.setText(provider.defaultModel.isEmpty() ? "No model set" : provider.defaultModel);
        model.setTextColor(ContextCompat.getColor(this,
            provider.defaultModel.isEmpty() ? R.color.koda_text_tertiary : R.color.koda_text_secondary));
        model.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        LinearLayout.LayoutParams modelLp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        modelLp.topMargin = dp(5);
        model.setLayoutParams(modelLp);
        card.addView(model);

        // ── Row 3: key status ──────────────────────────────────
        boolean hasKey = provider.hasApiKey();
        TextView keyStatus = new TextView(this);
        keyStatus.setText(hasKey ? "API key configured" : "No API key");
        keyStatus.setTextColor(ContextCompat.getColor(this,
            hasKey ? R.color.koda_success : R.color.koda_warning));
        keyStatus.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        LinearLayout.LayoutParams keyLp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        keyLp.topMargin = dp(3);
        keyStatus.setLayoutParams(keyLp);
        card.addView(keyStatus);

        // ── Row 4: action buttons ──────────────────────────────
        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams btnRowLp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        btnRowLp.topMargin = dp(12);
        btnRow.setLayoutParams(btnRowLp);
        btnRow.setGravity(Gravity.END);

        if (!isActive) {
            addCardButton(btnRow, "Use", true, v -> {
                mProviderMgr.setActiveProvider(provider.id);
                buildUI();
                Toast.makeText(this, "Switched to " + provider.name, Toast.LENGTH_SHORT).show();
            });
        }
        addCardButton(btnRow, "Edit", false, v -> showEditProviderDialog(provider));
        card.addView(btnRow);

        mContainer.addView(card);
    }

    /**
     * @param primary true = filled primary, false = ghost secondary
     */
    private void addCardButton(LinearLayout parent, String text, boolean primary,
                               View.OnClickListener onClick) {
        TextView btn = new TextView(this);
        btn.setText(text);
        btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        btn.setTypeface(null, Typeface.BOLD);
        btn.setGravity(Gravity.CENTER);
        btn.setPadding(dp(16), dp(7), dp(16), dp(7));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMarginStart(dp(8));
        btn.setLayoutParams(lp);

        if (primary) {
            btn.setTextColor(ContextCompat.getColor(this, R.color.koda_on_primary));
            btn.setBackground(ContextCompat.getDrawable(this, R.drawable.btn_primary));
        } else {
            btn.setTextColor(ContextCompat.getColor(this, R.color.koda_text_secondary));
            btn.setBackground(ContextCompat.getDrawable(this, R.drawable.btn_ghost));
        }

        btn.setOnClickListener(onClick);
        parent.addView(btn);
    }

    // =========================================================
    // Dialogs
    // =========================================================

    private void showEditProviderDialog(ProviderManager.Provider provider) {
        LinearLayout form = buildProviderForm(
            provider.name, provider.baseUrl, provider.apiKey, provider.defaultModel);
        EditText nameIn  = (EditText) ((LinearLayout) form.getChildAt(1)).getChildAt(0);
        EditText urlIn   = (EditText) ((LinearLayout) form.getChildAt(3)).getChildAt(0);
        EditText keyIn   = (EditText) ((LinearLayout) form.getChildAt(5)).getChildAt(0);
        EditText modelIn = (EditText) ((LinearLayout) form.getChildAt(7)).getChildAt(0);

        new AlertDialog.Builder(this, R.style.Theme_Koda_Dialog)
            .setTitle("Edit " + provider.name)
            .setView(form)
            .setPositiveButton("Save", (d, w) -> {
                provider.name         = nameIn.getText().toString().trim();
                provider.baseUrl      = urlIn.getText().toString().trim();
                provider.apiKey       = keyIn.getText().toString().trim();
                provider.defaultModel = modelIn.getText().toString().trim();
                mProviderMgr.updateProvider(provider);
                buildUI();
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void showAddProviderDialog() {
        LinearLayout form = buildProviderForm("", "", "", "");
        EditText nameIn  = (EditText) ((LinearLayout) form.getChildAt(1)).getChildAt(0);
        EditText urlIn   = (EditText) ((LinearLayout) form.getChildAt(3)).getChildAt(0);
        EditText keyIn   = (EditText) ((LinearLayout) form.getChildAt(5)).getChildAt(0);
        EditText modelIn = (EditText) ((LinearLayout) form.getChildAt(7)).getChildAt(0);

        new AlertDialog.Builder(this, R.style.Theme_Koda_Dialog)
            .setTitle("Add Provider")
            .setView(form)
            .setPositiveButton("Add", (d, w) -> {
                String newName = nameIn.getText().toString().trim();
                if (!newName.isEmpty()) {
                    ProviderManager.Provider p = new ProviderManager.Provider();
                    p.name         = newName;
                    p.baseUrl      = urlIn.getText().toString().trim();
                    p.apiKey       = keyIn.getText().toString().trim();
                    p.defaultModel = modelIn.getText().toString().trim();
                    p.models       = new String[]{};
                    p.type         = "anthropic";
                    mProviderMgr.addProvider(p);
                    buildUI();
                }
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    /**
     * Builds the provider form as a LinearLayout.
     * Structure: [label, [inputWrapper], label, [inputWrapper], ...]
     * Use getChildAt(1).getChildAt(0) for nameIn, etc.
     */
    private LinearLayout buildProviderForm(String name, String url, String key, String model) {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(20), dp(8), dp(20), dp(4));

        addFormField(form, "Name",          name,  "e.g. RelayGPU",
            InputType.TYPE_CLASS_TEXT);
        addFormField(form, "Base URL",       url,   "https://api.anthropic.com",
            InputType.TYPE_TEXT_VARIATION_URI | InputType.TYPE_CLASS_TEXT);
        addFormField(form, "API Key",        key,   "sk-ant-...",
            InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        addFormField(form, "Default Model",  model, "claude-sonnet-4-6",
            InputType.TYPE_CLASS_TEXT);
        return form;
    }

    private void addFormField(LinearLayout parent, String labelText, String value,
                              String hint, int inputType) {
        TextView label = new TextView(this);
        label.setText(labelText);
        label.setTextColor(ContextCompat.getColor(this, R.color.koda_text_secondary));
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        label.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        labelLp.topMargin = dp(12);
        label.setLayoutParams(labelLp);
        parent.addView(label);

        // Wrapper so getChildAt(n).getChildAt(0) always works
        LinearLayout wrapper = new LinearLayout(this);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        EditText input = new EditText(this);
        input.setText(value);
        input.setHint(hint);
        input.setInputType(inputType);
        input.setTextColor(ContextCompat.getColor(this, R.color.koda_text_primary));
        input.setHintTextColor(ContextCompat.getColor(this, R.color.koda_text_tertiary));
        input.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        input.setPadding(dp(12), dp(10), dp(12), dp(10));
        input.setBackground(ContextCompat.getDrawable(this, R.drawable.input_field_bg));
        LinearLayout.LayoutParams inputLp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        inputLp.topMargin = dp(4);
        input.setLayoutParams(inputLp);

        wrapper.addView(input);
        parent.addView(wrapper);
    }

    // =========================================================
    // Section headers + info rows
    // =========================================================

    private void addSectionHeader(String text) {
        TextView header = new TextView(this);
        header.setText(text);
        header.setTextColor(ContextCompat.getColor(this, R.color.koda_text_tertiary));
        header.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        header.setTypeface(null, Typeface.BOLD);
        header.setLetterSpacing(0.12f);
        header.setAllCaps(true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(dp(20), dp(24), dp(16), dp(8));
        header.setLayoutParams(lp);
        mContainer.addView(header);
    }

    /**
     * Adds an info row to a grouped card (not directly to mContainer).
     * @param divider true = add top divider (for rows after the first)
     */
    private void addInfoRow(LinearLayout group, String label, String value, boolean divider) {
        if (divider) {
            View sep = new View(this);
            LinearLayout.LayoutParams sepLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(1));
            sepLp.setMargins(dp(16), 0, dp(16), 0);
            sep.setLayoutParams(sepLp);
            sep.setBackgroundColor(ContextCompat.getColor(this, R.color.koda_stroke));
            group.addView(sep);
        }

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(dp(16), dp(12), dp(16), dp(12));
        row.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView labelTv = new TextView(this);
        labelTv.setText(label);
        labelTv.setTextColor(ContextCompat.getColor(this, R.color.koda_text_secondary));
        labelTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        labelTv.setLayoutParams(new LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(labelTv);

        TextView valueTv = new TextView(this);
        valueTv.setText(value);
        valueTv.setTextColor(ContextCompat.getColor(this, R.color.koda_text_primary));
        valueTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        row.addView(valueTv);

        group.addView(row);
    }

    private void addGhostButton(String text, View.OnClickListener onClick) {
        TextView btn = new TextView(this);
        btn.setText(text);
        btn.setTextColor(ContextCompat.getColor(this, R.color.koda_primary));
        btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        btn.setGravity(Gravity.CENTER);
        btn.setBackground(ContextCompat.getDrawable(this, R.drawable.btn_ghost));
        btn.setPadding(dp(16), dp(13), dp(16), dp(13));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(dp(16), dp(8), dp(16), dp(4));
        btn.setLayoutParams(lp);
        btn.setOnClickListener(onClick);
        mContainer.addView(btn);
    }

    // =========================================================
    // Helpers
    // =========================================================

    /** Borderless ripple from theme attribute. */
    private android.graphics.drawable.Drawable rippleBorderless() {
        int[] attrs = { android.R.attr.selectableItemBackgroundBorderless };
        android.content.res.TypedArray ta = obtainStyledAttributes(attrs);
        android.graphics.drawable.Drawable d = ta.getDrawable(0);
        ta.recycle();
        return d;
    }

    private int dp(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }
}
