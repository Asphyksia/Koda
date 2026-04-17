package dev.koda;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class OpenclaudeModelListUtilsTest {

    @Test
    public void buildPreferredModelListCommand_prefersModelRegistryAndFallsBackToOpenclaudeCli() {
        String command = OpenclaudeModelListUtils.buildPreferredModelListCommand();

        assertTrue(command.contains("model_registry_js=\"\""));
        assertTrue(command.contains("for candidate in \\"));
        assertTrue(command.contains(
            "$PREFIX/lib/node_modules/openclaude/node_modules/@mariozechner/pi-coding-agent/dist/core/model-registry.js"));
        assertFalse(command.contains(
            "$PREFIX/share/koda/openclaude-runtime/current/node_modules/@mariozechner/pi-coding-agent/dist/core/model-registry.js"));
        assertTrue(command.contains("if [ -f \"$candidate\" ]; then"));
        assertTrue(command.contains("model_registry_js=\"$candidate\""));
        assertTrue(command.contains("setFallbackResolver() {}"));
        assertTrue(command.contains("registry.getAll()"));
        assertTrue(command.contains("model.provider + '/' + model.id"));
        assertTrue(command.contains("termux-chroot"));
        assertTrue(command.contains("LD_LIBRARY_PATH"));
        assertTrue(command.contains("openclaude models list --all --plain"));
    }

    @Test
    public void fallbackModelListCommand_matchesOpenclaudeCli() {
        assertEquals(
            OpenclaudeVersionUtils.MODEL_LIST_COMMAND,
            OpenclaudeModelListUtils.FALLBACK_MODEL_LIST_COMMAND
        );
    }

    @Test
    public void buildPreferredModelListCommand_withTrace_enablesFallbackTracing() {
        String command = OpenclaudeModelListUtils.buildPreferredModelListCommand(true);

        assertTrue(command.contains("KODA_TRACE_NPM_REGISTRY=1 " + OpenclaudeVersionUtils.MODEL_LIST_COMMAND));
    }

    @Test
    public void buildPreferredModelListCommand_usesBuiltInRegistryWhenModelsJsonIsMissing() {
        String command = OpenclaudeModelListUtils.buildPreferredModelListCommand(true);

        assertTrue(command.contains("if [ -f \"$models_json\" ]; then"));
        assertTrue(command.contains("  export KODA_MODELS_JSON=\"$models_json\""));
        assertTrue(command.contains("  unset KODA_MODELS_JSON"));
        assertTrue(command.contains(
            "const registry = process.env.KODA_MODELS_JSON\n"
                + "  ? new ModelRegistry(fakeAuth, process.env.KODA_MODELS_JSON)\n"
                + "  : new ModelRegistry(fakeAuth);"));
    }
}
