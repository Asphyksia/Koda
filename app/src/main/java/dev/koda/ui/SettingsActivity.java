package dev.koda.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import com.termux.R;
import androidx.appcompat.app.AlertDialog;
import java.util.List;
import dev.koda.data.ProviderManager;
/**
 * Settings screen for managing LLM providers.
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
        scroll.setFillViewport(true);
        mContainer = new LinearLayout(this);
        mContainer.setOrientation(LinearLayout.VERTICAL);
        mContainer.setPadding(0, 0, 0, dp(32));
        scroll.addView(mContainer);
        setContentView(scroll);
        buildUI();
    }
    private void buildUI() {
        mContainer.removeAllViews();
        // Top bar
        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setBackgroundColor(ContextCompat.getColor(this, R.color.koda_surface_1));
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setPadding(dp(8), 0, dp(16), 0);
        LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(56));
        topBar.setLayoutParams(barParams);
        ImageButton back = new ImageButton(this);
        back.setImageResource(android.R.drawable.ic_menu_revert);
        back.setBackgroundColor(Color.TRANSPARENT);
        back.setColorFilter(ContextCompat.getColor(this, R.color.koda_text_secondary));
        back.setOnClickListener(v -> finish());
        back.setLayoutParams(new LinearLayout.LayoutParams(dp(40), dp(40)));
        topBar.addView(back);
        TextView title = new TextView(this);
        title.setText("Settings");
        title.setTextColor(ContextCompat.getColor(this, R.color.koda_text_primary));
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        title.setTypeface(null, Typeface.BOLD);
        title.setPadding(dp(8), 0, 0, 0);
        topBar.addView(title);
        mContainer.addView(topBar);
        // Section: Providers
        addSectionHeader("LLM Providers");
        List<ProviderManager.Provider> providers = mProviderMgr.getProviders();
        ProviderManager.Provider active = mProviderMgr.getActiveProvider();
        for (ProviderManager.Provider p : providers) {
            addProviderCard(p, active != null && p.id.equals(active.id));
        }
        // Add provider button
        addButton("+ Add Provider", v -> showAddProviderDialog());
        // Section: About
        addSectionHeader("About");
        addInfoRow("Version", "0.3.0");
        addInfoRow("Engine", "OpenClaude CLI v0.4.0");
        addInfoRow("Runtime", "Termux (aarch64)");
    // ========== Provider Card ==========
    private void addProviderCard(ProviderManager.Provider provider, boolean isActive) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT);
        cardParams.setMargins(dp(16), dp(8), dp(16), dp(4));
        card.setLayoutParams(cardParams);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(ContextCompat.getColor(this, R.color.koda_surface_1));
        bg.setCornerRadius(dp(12));
        if (isActive) {
            bg.setStroke(dp(2), ContextCompat.getColor(this, R.color.koda_primary));
        card.setBackground(bg);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        // Row 1: Name + active badge
        LinearLayout row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        row1.setGravity(Gravity.CENTER_VERTICAL);
        TextView name = new TextView(this);
        name.setText(provider.name);
        name.setTextColor(ContextCompat.getColor(this, R.color.koda_text_primary));
        name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        name.setTypeface(null, Typeface.BOLD);
        name.setLayoutParams(new LinearLayout.LayoutParams(0,
            LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        row1.addView(name);
            TextView badge = new TextView(this);
            badge.setText("ACTIVE");
            badge.setTextColor(ContextCompat.getColor(this, R.color.koda_primary));
            badge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            badge.setTypeface(null, Typeface.BOLD);
            GradientDrawable badgeBg = new GradientDrawable();
            badgeBg.setColor(ContextCompat.getColor(this, R.color.koda_primary_container));
            badgeBg.setCornerRadius(dp(6));
            badge.setBackground(badgeBg);
            badge.setPadding(dp(8), dp(3), dp(8), dp(3));
            row1.addView(badge);
        card.addView(row1);
        // Row 2: Model
        TextView model = new TextView(this);
        model.setText("Model: " + (provider.defaultModel.isEmpty() ? "not set" : provider.defaultModel));
        model.setTextColor(ContextCompat.getColor(this, R.color.koda_text_secondary));
        model.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        LinearLayout.LayoutParams modelParams = new LinearLayout.LayoutParams(
        modelParams.topMargin = dp(4);
        model.setLayoutParams(modelParams);
        card.addView(model);
        // Row 3: API Key status
        TextView keyStatus = new TextView(this);
        keyStatus.setText(provider.hasApiKey() ? "🔑 API key configured" : "⚠ No API key");
        keyStatus.setTextColor(provider.hasApiKey() ?
            ContextCompat.getColor(this, R.color.koda_success) : ContextCompat.getColor(this, R.color.koda_warning));
        keyStatus.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        LinearLayout.LayoutParams keyParams = new LinearLayout.LayoutParams(
        keyParams.topMargin = dp(4);
        keyStatus.setLayoutParams(keyParams);
        card.addView(keyStatus);
        // Row 4: Buttons
        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams btnRowParams = new LinearLayout.LayoutParams(
        btnRowParams.topMargin = dp(10);
        buttons.setLayoutParams(btnRowParams);
        buttons.setGravity(Gravity.END);
        if (!isActive) {
            addCardButton(buttons, "Use", "#7DD3FC", "#0C4A6E", v -> {
                mProviderMgr.setActiveProvider(provider.id);
                buildUI();
                Toast.makeText(this, "Switched to " + provider.name, Toast.LENGTH_SHORT).show();
            });
        addCardButton(buttons, "Edit", "#94A3B8", "#334155", v -> {
            showEditProviderDialog(provider);
        });
        card.addView(buttons);
        mContainer.addView(card);
    private void addCardButton(LinearLayout parent, String text, String textColor,
                               String bgColor, View.OnClickListener onClick) {
        TextView btn = new TextView(this);
        btn.setText(text);
        btn.setTextColor(Color.parseColor(textColor));
        btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        btn.setTypeface(null, Typeface.BOLD);
        bg.setColor(Color.parseColor(bgColor));
        bg.setCornerRadius(dp(8));
        btn.setBackground(bg);
        btn.setPadding(dp(14), dp(6), dp(14), dp(6));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
        lp.setMarginStart(dp(8));
        btn.setLayoutParams(lp);
        btn.setOnClickListener(onClick);
        parent.addView(btn);
    // ========== Dialogs ==========
    private void showEditProviderDialog(ProviderManager.Provider provider) {
        LinearLayout form = createProviderForm(provider);
        EditText nameInput = (EditText) form.getChildAt(1);
        EditText urlInput = (EditText) form.getChildAt(3);
        EditText keyInput = (EditText) form.getChildAt(5);
        EditText modelInput = (EditText) form.getChildAt(7);
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog)
            .setTitle("Edit " + provider.name)
            .setView(form)
            .setPositiveButton("Save", (d, w) -> {
                provider.name = nameInput.getText().toString().trim();
                provider.baseUrl = urlInput.getText().toString().trim();
                provider.apiKey = keyInput.getText().toString().trim();
                provider.defaultModel = modelInput.getText().toString().trim();
                mProviderMgr.updateProvider(provider);
            })
            .setNegativeButton("Cancel", null)
            .show();
    private void showAddProviderDialog() {
        ProviderManager.Provider newP = new ProviderManager.Provider();
        newP.name = "";
        newP.baseUrl = "";
        newP.apiKey = "";
        newP.defaultModel = "";
        newP.models = new String[]{};
        newP.type = "anthropic";
        LinearLayout form = createProviderForm(newP);
            .setTitle("Add Provider")
            .setPositiveButton("Add", (d, w) -> {
                newP.name = nameInput.getText().toString().trim();
                newP.baseUrl = urlInput.getText().toString().trim();
                newP.apiKey = keyInput.getText().toString().trim();
                newP.defaultModel = modelInput.getText().toString().trim();
                if (!newP.name.isEmpty()) {
                    mProviderMgr.addProvider(newP);
                    buildUI();
                }
    private LinearLayout createProviderForm(ProviderManager.Provider p) {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(20), dp(12), dp(20), dp(4));
        addFormLabel(form, "Name");
        addFormInput(form, p.name, "e.g. RelayGPU", InputType.TYPE_CLASS_TEXT);
        addFormLabel(form, "Base URL");
        addFormInput(form, p.baseUrl, "https://api.anthropic.com", InputType.TYPE_TEXT_VARIATION_URI);
        addFormLabel(form, "API Key");
        addFormInput(form, p.apiKey, "sk-...", InputType.TYPE_TEXT_VARIATION_PASSWORD);
        addFormLabel(form, "Default Model");
        addFormInput(form, p.defaultModel, "claude-sonnet-4-6", InputType.TYPE_CLASS_TEXT);
        return form;
    private void addFormLabel(LinearLayout parent, String text) {
        TextView label = new TextView(this);
        label.setText(text);
        label.setTextColor(ContextCompat.getColor(this, R.color.koda_text_secondary));
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        lp.topMargin = dp(10);
        label.setLayoutParams(lp);
        parent.addView(label);
    private void addFormInput(LinearLayout parent, String value, String hint, int inputType) {
        EditText input = new EditText(this);
        input.setText(value);
        input.setHint(hint);
        input.setInputType(inputType);
        input.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        input.setPadding(dp(12), dp(10), dp(12), dp(10));
        lp.topMargin = dp(4);
        input.setLayoutParams(lp);
        parent.addView(input);
    // ========== UI Helpers ==========
    private void addSectionHeader(String text) {
        TextView header = new TextView(this);
        header.setText(text);
        header.setTextColor(ContextCompat.getColor(this, R.color.koda_primary));
        header.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        header.setTypeface(null, Typeface.BOLD);
        header.setLetterSpacing(0.1f);
        header.setAllCaps(true);
        lp.setMargins(dp(16), dp(20), dp(16), dp(4));
        header.setLayoutParams(lp);
        mContainer.addView(header);
    private void addInfoRow(String label, String value) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
        rowParams.setMargins(dp(16), dp(6), dp(16), dp(2));
        row.setLayoutParams(rowParams);
        row.setPadding(dp(16), dp(10), dp(16), dp(10));
        row.setBackground(bg);
        TextView labelTv = new TextView(this);
        labelTv.setText(label);
        labelTv.setTextColor(ContextCompat.getColor(this, R.color.koda_text_secondary));
        labelTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        labelTv.setLayoutParams(new LinearLayout.LayoutParams(0,
        row.addView(labelTv);
        TextView valueTv = new TextView(this);
        valueTv.setText(value);
        valueTv.setTextColor(ContextCompat.getColor(this, R.color.koda_text_primary));
        valueTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        row.addView(valueTv);
        mContainer.addView(row);
    private void addButton(String text, View.OnClickListener onClick) {
        btn.setTextColor(ContextCompat.getColor(this, R.color.koda_primary));
        btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        btn.setGravity(Gravity.CENTER);
        lp.setMargins(dp(16), dp(12), dp(16), dp(4));
        btn.setPadding(dp(16), dp(14), dp(16), dp(14));
        bg.setColor(ContextCompat.getColor(this, R.color.koda_bg));
        bg.setStroke(dp(1), ContextCompat.getColor(this, R.color.koda_stroke_strong));
        mContainer.addView(btn);
    private int dp(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
}
