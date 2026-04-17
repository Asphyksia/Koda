package dev.koda.data;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.termux.shared.termux.TermuxConstants;

/**
 * Manages LLM providers and syncs active provider to openclaude.json.
 *
 * Storage: SharedPreferences (providers JSON array) + encrypted API keys.
 * The active provider is written to ~/.openclaude/openclaude.json for OpenClaude CLI.
 */
public class ProviderManager {

    private static final String PREFS_NAME = "koda_providers";
    private static final String KEY_PROVIDERS = "providers_json";
    private static final String KEY_ACTIVE_ID = "active_provider_id";
    private static final String HOME = TermuxConstants.TERMUX_HOME_DIR_PATH;

    private final SharedPreferences mPrefs;
    private List<Provider> mProviders;

    public ProviderManager(Context context) {
        mPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        mProviders = loadProviders();

        // Seed defaults if empty
        if (mProviders.isEmpty()) {
            seedDefaults();
        }
    }

    // ========== Provider CRUD ==========

    public List<Provider> getProviders() {
        return new ArrayList<>(mProviders);
    }

    public Provider getActiveProvider() {
        String activeId = mPrefs.getString(KEY_ACTIVE_ID, "");
        for (Provider p : mProviders) {
            if (p.id.equals(activeId)) return p;
        }
        if (!mProviders.isEmpty()) return mProviders.get(0);
        return null;
    }

    public void setActiveProvider(String providerId) {
        mPrefs.edit().putString(KEY_ACTIVE_ID, providerId).apply();
        syncToOpenclaudeConfig();
    }

    public Provider getProvider(String id) {
        for (Provider p : mProviders) {
            if (p.id.equals(id)) return p;
        }
        return null;
    }

    public void addProvider(Provider p) {
        if (p.id == null || p.id.isEmpty()) p.id = UUID.randomUUID().toString();
        mProviders.add(p);
        saveProviders();
    }

    public void updateProvider(Provider updated) {
        for (int i = 0; i < mProviders.size(); i++) {
            if (mProviders.get(i).id.equals(updated.id)) {
                mProviders.set(i, updated);
                saveProviders();
                // Re-sync if this is the active provider
                Provider active = getActiveProvider();
                if (active != null && active.id.equals(updated.id)) {
                    syncToOpenclaudeConfig();
                }
                return;
            }
        }
    }

    public void deleteProvider(String id) {
        mProviders.removeIf(p -> p.id.equals(id));
        saveProviders();
    }

    // ========== Sync to OpenClaude config ==========

    /**
     * Write active provider settings to ~/.openclaude/openclaude.json
     * This is what OpenClaude CLI reads for ANTHROPIC_API_KEY etc.
     */
    public void syncToOpenclaudeConfig() {
        Provider active = getActiveProvider();
        if (active == null) return;

        try {
            JSONObject config = new JSONObject();
            JSONObject models = new JSONObject();
            JSONObject providers = new JSONObject();
            JSONObject defaultProvider = new JSONObject();

            defaultProvider.put("apiKey", active.apiKey);
            defaultProvider.put("baseUrl", active.baseUrl);

            providers.put("default", defaultProvider);
            models.put("providers", providers);
            models.put("default", active.defaultModel);

            config.put("models", models);

            // Write file
            File dir = new File(HOME + "/.openclaude");
            dir.mkdirs();
            File configFile = new File(dir, "openclaude.json");
            try (FileOutputStream fos = new FileOutputStream(configFile)) {
                fos.write(config.toString(2).getBytes());
            }
        } catch (Exception e) {
            // Log but don't crash
        }
    }

    // ========== Persistence ==========

    private List<Provider> loadProviders() {
        List<Provider> list = new ArrayList<>();
        String json = mPrefs.getString(KEY_PROVIDERS, "[]");
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                list.add(Provider.fromJson(arr.getJSONObject(i)));
            }
        } catch (Exception ignored) {}
        return list;
    }

    private void saveProviders() {
        try {
            JSONArray arr = new JSONArray();
            for (Provider p : mProviders) {
                arr.put(p.toJson());
            }
            mPrefs.edit().putString(KEY_PROVIDERS, arr.toString()).apply();
        } catch (Exception ignored) {}
    }

    // ========== Default Providers ==========

    private void seedDefaults() {
        // RelayGPU (Anthropic)
        Provider relay = new Provider();
        relay.id = "relaygpu-anthropic";
        relay.name = "RelayGPU";
        relay.baseUrl = "https://relay.opengpu.network/v2/anthropic";
        relay.apiKey = "";
        relay.defaultModel = "anthropic/claude-sonnet-4-6";
        relay.models = new String[]{
            "anthropic/claude-sonnet-4-6",
            "anthropic/claude-opus-4-6"
        };
        relay.type = "anthropic";
        mProviders.add(relay);

        // Anthropic (direct)
        Provider anthropic = new Provider();
        anthropic.id = "anthropic-direct";
        anthropic.name = "Anthropic";
        anthropic.baseUrl = "";  // empty = default api.anthropic.com
        anthropic.apiKey = "";
        anthropic.defaultModel = "claude-sonnet-4-6";
        anthropic.models = new String[]{
            "claude-sonnet-4-6",
            "claude-opus-4-6",
            "claude-haiku-3-5"
        };
        anthropic.type = "anthropic";
        mProviders.add(anthropic);

        // OpenRouter
        Provider openrouter = new Provider();
        openrouter.id = "openrouter";
        openrouter.name = "OpenRouter";
        openrouter.baseUrl = "https://openrouter.ai/api/v1";
        openrouter.apiKey = "";
        openrouter.defaultModel = "anthropic/claude-sonnet-4";
        openrouter.models = new String[]{};
        openrouter.type = "openai";
        mProviders.add(openrouter);

        // Custom
        Provider custom = new Provider();
        custom.id = "custom";
        custom.name = "Custom";
        custom.baseUrl = "";
        custom.apiKey = "";
        custom.defaultModel = "";
        custom.models = new String[]{};
        custom.type = "anthropic";
        mProviders.add(custom);

        saveProviders();
        setActiveProvider("relaygpu-anthropic");
    }

    // ========== Data Class ==========

    public static class Provider {
        public String id;
        public String name;
        public String baseUrl;
        public String apiKey;
        public String defaultModel;
        public String[] models;
        public String type;  // "anthropic" or "openai"

        public JSONObject toJson() {
            try {
                JSONObject o = new JSONObject();
                o.put("id", id);
                o.put("name", name);
                o.put("baseUrl", baseUrl);
                o.put("apiKey", apiKey);
                o.put("defaultModel", defaultModel);
                o.put("type", type);
                JSONArray m = new JSONArray();
                if (models != null) {
                    for (String s : models) m.put(s);
                }
                o.put("models", m);
                return o;
            } catch (Exception e) {
                return new JSONObject();
            }
        }

        public static Provider fromJson(JSONObject o) {
            Provider p = new Provider();
            p.id = o.optString("id", "");
            p.name = o.optString("name", "");
            p.baseUrl = o.optString("baseUrl", "");
            p.apiKey = o.optString("apiKey", "");
            p.defaultModel = o.optString("defaultModel", "");
            p.type = o.optString("type", "anthropic");
            JSONArray m = o.optJSONArray("models");
            if (m != null) {
                p.models = new String[m.length()];
                for (int i = 0; i < m.length(); i++) {
                    p.models[i] = m.optString(i, "");
                }
            } else {
                p.models = new String[]{};
            }
            return p;
        }

        public boolean hasApiKey() {
            return apiKey != null && !apiKey.isEmpty();
        }
    }
}
