package dev.koda;

/**
 * Builds the preferred model-list command.
 *
 * Fast path: load models through pi-coding-agent's ModelRegistry without starting the full
 * OpenClaude CLI bundle. If that path fails for any reason, fall back to the standard OpenClaude
 * command so behavior remains compatible.
 */
public final class OpenclaudeModelListUtils {

    public static final String FALLBACK_MODEL_LIST_COMMAND = OpenclaudeVersionUtils.MODEL_LIST_COMMAND;

    private static final String MODELS_JSON_PATH = "$HOME/.openclaude/agents/main/models.json";

    private OpenclaudeModelListUtils() {}

    public static String buildPreferredModelListCommand() {
        return buildPreferredModelListCommand(false);
    }

    public static String buildPreferredModelListCommand(boolean enableTrace) {
        String fallbackCommand = OpenclaudeVersionUtils.buildModelListCommand(enableTrace);
        return String.join("\n",
            "export LD_LIBRARY_PATH=\"$PREFIX/lib${LD_LIBRARY_PATH:+:$LD_LIBRARY_PATH}\"",
            "model_registry_js=\"\"",
            "models_json=\"" + MODELS_JSON_PATH + "\"",
            "probe_script=\"$TMPDIR/koda-model-list-$$.mjs\"",
            "cleanup() {",
            "  rm -f \"$probe_script\"",
            "}",
            "trap cleanup EXIT",
            "for candidate in \\",
            "  \"$PREFIX/lib/node_modules/openclaude/node_modules/@mariozechner/pi-coding-agent/dist/core/model-registry.js\" \\",
            "  \"$PREFIX/lib/node_modules/@mariozechner/pi-coding-agent/dist/core/model-registry.js\"; do",
            "  if [ -f \"$candidate\" ]; then",
            "    model_registry_js=\"$candidate\"",
            "    break",
            "  fi",
            "done",
            "if [ -x \"$PREFIX/bin/node\" ] && [ -x \"$PREFIX/bin/termux-chroot\" ] && [ -f \"$model_registry_js\" ]; then",
            "  cat > \"$probe_script\" <<'EOF'",
            "const { ModelRegistry } = await import(`file://${process.env.KODA_MODEL_REGISTRY_JS}`);",
            "const fakeAuth = {",
            "  setFallbackResolver() {},",
            "  getOAuthProviders() { return []; },",
            "  get() { return undefined; },",
            "  hasAuth() { return false; },",
            "  getApiKey() { return undefined; },",
            "};",
            "const registry = process.env.KODA_MODELS_JSON",
            "  ? new ModelRegistry(fakeAuth, process.env.KODA_MODELS_JSON)",
            "  : new ModelRegistry(fakeAuth);",
            "const uniqueLines = [...new Set(",
            "  registry.getAll()",
            "    .map((model) => model.provider + '/' + model.id)",
            "    .filter((line) => typeof line === 'string' && line.includes('/')",
            "      && !line.startsWith('undefined/') && !line.endsWith('/undefined'))",
            ")];",
            "if (!uniqueLines.length) {",
            "  process.exit(1);",
            "}",
            "process.stdout.write(uniqueLines.join('\\n'));",
            "process.stdout.write('\\n');",
            "EOF",
            "  export KODA_MODEL_REGISTRY_JS=\"$model_registry_js\"",
            "  if [ -f \"$models_json\" ]; then",
            "    export KODA_MODELS_JSON=\"$models_json\"",
            "  else",
            "    unset KODA_MODELS_JSON",
            "  fi",
            "  if \"$PREFIX/bin/termux-chroot\" \"$PREFIX/bin/node\" \"$probe_script\"; then",
            "    exit 0",
            "  fi",
            "fi",
            fallbackCommand
        );
    }
}
