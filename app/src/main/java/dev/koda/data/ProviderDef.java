package dev.koda.data;

import java.util.Arrays;
import java.util.List;

/**
 * Immutable definition of an LLM provider supported by Koda.
 * Describes display metadata, auth requirements, and available access modes.
 */
public class ProviderDef {

    public static final String API_TYPE_OPENAI      = "openai-completions";
    public static final String API_TYPE_ANTHROPIC   = "anthropic-messages";

    /** Provider ID — used in openclaude.json and auth-profiles.json */
    public final String id;

    /** Display name shown in the UI */
    public final String displayName;

    /** Short description shown in the provider card */
    public final String description;

    /** Emoji or short label used as icon placeholder */
    public final String iconEmoji;

    /** Available access modes. If only one, mode selector is hidden. */
    public final List<AccessMode> modes;

    public ProviderDef(String id, String displayName, String description, String iconEmoji, List<AccessMode> modes) {
        this.id = id;
        this.displayName = displayName;
        this.description = description;
        this.iconEmoji = iconEmoji;
        this.modes = modes;
    }

    /**
     * Describes one access mode for a provider (e.g. "Standard API", "Anthropic endpoint").
     */
    public static class AccessMode {
        /** Display label for this mode */
        public final String label;

        /** OpenClaude API type identifier */
        public final String apiType;

        /** Pre-set base URL. Empty string = user must provide it. */
        public final String baseUrl;

        /** Whether to show a manual endpoint URL field */
        public final boolean requiresCustomUrl;

        /** Auth fields required */
        public final boolean requiresApiKey;

        /** Suggested model IDs for this mode */
        public final List<String> suggestedModels;

        /** Default model to pre-select */
        public final String defaultModel;

        public AccessMode(String label, String apiType, String baseUrl,
                          boolean requiresCustomUrl, boolean requiresApiKey,
                          List<String> suggestedModels, String defaultModel) {
            this.label = label;
            this.apiType = apiType;
            this.baseUrl = baseUrl;
            this.requiresCustomUrl = requiresCustomUrl;
            this.requiresApiKey = requiresApiKey;
            this.suggestedModels = suggestedModels;
            this.defaultModel = defaultModel;
        }
    }

    // ─── Convenience constructors ─────────────────────────────────────────────

    /** Single standard API key mode */
    public static ProviderDef standard(String id, String displayName, String description,
                                        String iconEmoji, String apiType, String baseUrl,
                                        String defaultModel, String... models) {
        AccessMode mode = new AccessMode(
            "Standard API", apiType, baseUrl,
            false, true,
            Arrays.asList(models), defaultModel
        );
        return new ProviderDef(id, displayName, description, iconEmoji,
            Arrays.asList(mode));
    }
}
