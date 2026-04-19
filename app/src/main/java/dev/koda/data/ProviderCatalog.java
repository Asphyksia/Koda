package dev.koda.data;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Static catalog of all LLM providers supported by Koda.
 * Add new providers here — no other code changes needed for basic support.
 */
public final class ProviderCatalog {

    private ProviderCatalog() {}

    // ─── RelayGPU ─────────────────────────────────────────────────────────────
    public static final ProviderDef RELAYGPU = new ProviderDef(
        "relaygpu",
        "RelayGPU",
        "Multi-model relay — OpenAI and Anthropic endpoints",
        "⚡",
        Arrays.asList(
            new ProviderDef.AccessMode(
                "Anthropic Models",
                ProviderDef.API_TYPE_ANTHROPIC,
                "https://relay.opengpu.network/v2/anthropic/v1",
                false, true,
                Arrays.asList(
                    "anthropic/claude-sonnet-4-6",
                    "anthropic/claude-opus-4-6"
                ),
                "anthropic/claude-sonnet-4-6"
            ),
            new ProviderDef.AccessMode(
                "OpenAI-Compatible",
                ProviderDef.API_TYPE_OPENAI,
                "https://relay.opengpu.network/v2/openai/v1",
                false, true,
                Arrays.asList(
                    "openai/gpt-5.2",
                    "openai/gpt-5.4",
                    "deepseek-ai/DeepSeek-V3.1",
                    "Qwen/Qwen3.5-397B-A17B-FP8",
                    "moonshotai/kimi-k2.5",
                    "infercom/DeepSeek-V3.1",
                    "infercom/MiniMax-M2.5"
                ),
                "openai/gpt-5.2"
            )
        )
    );

    // ─── Anthropic ────────────────────────────────────────────────────────────
    public static final ProviderDef ANTHROPIC = ProviderDef.standard(
        "anthropic", "Anthropic", "Claude models — direct API",
        "🤖",
        ProviderDef.API_TYPE_ANTHROPIC,
        "https://api.anthropic.com/v1",
        "claude-sonnet-4-5",
        "claude-opus-4-5", "claude-sonnet-4-5", "claude-haiku-3-5"
    );

    // ─── OpenAI ───────────────────────────────────────────────────────────────
    public static final ProviderDef OPENAI = ProviderDef.standard(
        "openai", "OpenAI", "GPT models — direct API",
        "🟢",
        ProviderDef.API_TYPE_OPENAI,
        "https://api.openai.com/v1",
        "gpt-4o",
        "gpt-4o", "gpt-4o-mini", "o3", "o4-mini"
    );

    // ─── OpenRouter ───────────────────────────────────────────────────────────
    public static final ProviderDef OPENROUTER = ProviderDef.standard(
        "openrouter", "OpenRouter", "Unified gateway to 200+ models",
        "🔀",
        ProviderDef.API_TYPE_OPENAI,
        "https://openrouter.ai/api/v1",
        "anthropic/claude-sonnet-4-5",
        "anthropic/claude-opus-4-5", "anthropic/claude-sonnet-4-5",
        "openai/gpt-4o", "google/gemini-2.0-flash"
    );

    // ─── Groq ─────────────────────────────────────────────────────────────────
    public static final ProviderDef GROQ = ProviderDef.standard(
        "groq", "Groq", "Ultra-fast inference",
        "🚀",
        ProviderDef.API_TYPE_OPENAI,
        "https://api.groq.com/openai/v1",
        "llama-3.3-70b-versatile",
        "llama-3.3-70b-versatile", "llama-3.1-8b-instant", "moonshotai/kimi-k2-instruct"
    );

    // ─── DeepSeek ─────────────────────────────────────────────────────────────
    public static final ProviderDef DEEPSEEK = ProviderDef.standard(
        "deepseek", "DeepSeek", "Reasoning and coding models",
        "🐋",
        ProviderDef.API_TYPE_OPENAI,
        "https://api.deepseek.com/v1",
        "deepseek-chat",
        "deepseek-chat", "deepseek-reasoner"
    );

    // ─── Mistral ──────────────────────────────────────────────────────────────
    public static final ProviderDef MISTRAL = ProviderDef.standard(
        "mistral", "Mistral", "European open-weight models",
        "🌊",
        ProviderDef.API_TYPE_OPENAI,
        "https://api.mistral.ai/v1",
        "mistral-large-latest",
        "mistral-large-latest", "mistral-small-latest", "codestral-latest"
    );

    // ─── Together AI ──────────────────────────────────────────────────────────
    public static final ProviderDef TOGETHER = ProviderDef.standard(
        "together", "Together AI", "Open-source models at scale",
        "🤝",
        ProviderDef.API_TYPE_OPENAI,
        "https://api.together.xyz/v1",
        "meta-llama/Llama-3.3-70B-Instruct-Turbo",
        "meta-llama/Llama-3.3-70B-Instruct-Turbo",
        "deepseek-ai/DeepSeek-V3",
        "Qwen/Qwen3-235B-A22B"
    );

    // ─── NVIDIA NIM ───────────────────────────────────────────────────────────
    public static final ProviderDef NVIDIA = ProviderDef.standard(
        "nvidia", "NVIDIA NIM", "133+ models on NVIDIA GPU cloud",
        "🖥️",
        ProviderDef.API_TYPE_OPENAI,
        "https://integrate.api.nvidia.com/v1",
        "meta/llama-3.3-70b-instruct",
        "meta/llama-3.3-70b-instruct",
        "nvidia/llama-3.3-nemotron-super-49b-v1",
        "nvidia/llama-3.1-nemotron-ultra-253b-v1",
        "qwen/qwen3.5-397b-a17b",
        "mistralai/mistral-large-3-675b-instruct-2512",
        "deepseek-ai/deepseek-v3.2",
        "meta/llama-4-maverick-17b-128e-instruct"
    );

    // ─── Azure OpenAI ─────────────────────────────────────────────────────────
    public static final ProviderDef AZURE = new ProviderDef(
        "azure",
        "Azure OpenAI",
        "Microsoft-hosted OpenAI — custom endpoint",
        "☁️",
        Collections.singletonList(
            new ProviderDef.AccessMode(
                "Custom Deployment",
                ProviderDef.API_TYPE_OPENAI,
                "",   // user must supply their deployment URL
                true, true,
                Arrays.asList("gpt-4o", "gpt-4o-mini", "o3"),
                "gpt-4o"
            )
        )
    );

    // ─── Custom ───────────────────────────────────────────────────────────────
    public static final ProviderDef CUSTOM = new ProviderDef(
        "custom",
        "Custom Endpoint",
        "Any OpenAI-compatible API",
        "🔧",
        Collections.singletonList(
            new ProviderDef.AccessMode(
                "OpenAI-Compatible",
                ProviderDef.API_TYPE_OPENAI,
                "",
                true, true,
                Collections.emptyList(),
                ""
            )
        )
    );

    // ─── Priority-ordered lists ───────────────────────────────────────────────

    /** Tier 1 — shown prominently at top */
    public static final List<ProviderDef> PRIORITY = Arrays.asList(
        RELAYGPU, ANTHROPIC, OPENAI, OPENROUTER, GROQ, DEEPSEEK
    );

    /** Tier 2 — shown in "More providers" section */
    public static final List<ProviderDef> SECONDARY = Arrays.asList(
        MISTRAL, TOGETHER, NVIDIA, AZURE
    );

    /** Tier 3 — always at bottom */
    public static final List<ProviderDef> ADVANCED = Collections.singletonList(
        CUSTOM
    );

    /** Flat list in display order */
    public static List<ProviderDef> all() {
        List<ProviderDef> all = new java.util.ArrayList<>();
        all.addAll(PRIORITY);
        all.addAll(SECONDARY);
        all.addAll(ADVANCED);
        return Collections.unmodifiableList(all);
    }

    /** Find a provider by its ID. Returns null if not found. */
    public static ProviderDef findById(String id) {
        if (id == null) return null;
        for (ProviderDef p : all()) {
            if (p.id.equals(id)) return p;
        }
        return null;
    }
}
