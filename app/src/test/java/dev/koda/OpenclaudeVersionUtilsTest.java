package dev.koda;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class OpenclaudeVersionUtilsTest {

    @Test
    public void testParseVersions_jsonArray() {
        String json = "[\"1.0.0\", \"1.1.0\", \"2.0.0-beta.1\", \"1.2.0\"]";
        List<String> result = OpenclaudeVersionUtils.parseVersions(json);
        // Should exclude pre-release, sort desc
        assertFalse(result.isEmpty());
        assertTrue(result.contains("1.2.0"));
        assertTrue(result.contains("1.1.0"));
        assertTrue(result.contains("1.0.0"));
        assertFalse(result.contains("2.0.0-beta.1"));
        assertEquals("1.2.0", result.get(0));
    }

    @Test
    public void testParseVersions_emptyInput() {
        assertTrue(OpenclaudeVersionUtils.parseVersions(null).isEmpty());
        assertTrue(OpenclaudeVersionUtils.parseVersions("").isEmpty());
        assertTrue(OpenclaudeVersionUtils.parseVersions("  ").isEmpty());
    }

    @Test
    public void testParseVersions_lineBasedFallback() {
        String lines = "1.0.0\n1.1.0\n1.2.0";
        List<String> result = OpenclaudeVersionUtils.parseVersions(lines);
        assertFalse(result.isEmpty());
        assertEquals("1.2.0", result.get(0));
    }

    @Test
    public void testBuildFallback_withCurrentVersion() {
        List<String> result = OpenclaudeVersionUtils.buildFallback("2026.2.6");
        assertTrue(result.contains("latest"));
        assertTrue(result.contains("2026.2.6"));
    }

    @Test
    public void testBuildFallback_nullCurrentVersion() {
        List<String> result = OpenclaudeVersionUtils.buildFallback(null);
        assertTrue(result.contains("latest"));
        assertEquals(1, result.size());
    }

    @Test
    public void testNormalizeVersionList_filtersPreRelease() {
        List<String> input = Arrays.asList("1.0.0", "2.0.0-alpha", "1.1.0", "latest");
        List<String> result = OpenclaudeVersionUtils.normalizeVersionList(input);
        assertTrue(result.contains("1.1.0"));
        assertTrue(result.contains("1.0.0"));
        assertTrue(result.contains("latest"));
        assertFalse(result.contains("2.0.0-alpha"));
    }

    @Test
    public void testNormalizeInstallVersion() {
        assertEquals("openclaude@1.2.3", OpenclaudeVersionUtils.normalizeInstallVersion("1.2.3"));
        assertEquals("openclaude@latest", OpenclaudeVersionUtils.normalizeInstallVersion("latest"));
        assertEquals("openclaude@1.2.3", OpenclaudeVersionUtils.normalizeInstallVersion("openclaude@1.2.3"));
        assertEquals("openclaude@1.2.3", OpenclaudeVersionUtils.normalizeInstallVersion("v1.2.3"));
        assertNull(OpenclaudeVersionUtils.normalizeInstallVersion(null));
        assertNull(OpenclaudeVersionUtils.normalizeInstallVersion(""));
    }

    @Test
    public void testNormalizeForSort() {
        assertEquals("1.2.3", OpenclaudeVersionUtils.normalizeForSort("openclaude@1.2.3"));
        assertEquals("1.2.3", OpenclaudeVersionUtils.normalizeForSort("v1.2.3"));
        assertEquals("1.2.3", OpenclaudeVersionUtils.normalizeForSort("1.2.3"));
        assertEquals("latest", OpenclaudeVersionUtils.normalizeForSort("latest"));
        assertNull(OpenclaudeVersionUtils.normalizeForSort(null));
        assertNull(OpenclaudeVersionUtils.normalizeForSort(""));
        assertNull(OpenclaudeVersionUtils.normalizeForSort("openclaude@"));
    }

    @Test
    public void testIsStableVersion() {
        assertTrue(OpenclaudeVersionUtils.isStableVersion("1.0.0"));
        assertTrue(OpenclaudeVersionUtils.isStableVersion("2026.2.6"));
        assertFalse(OpenclaudeVersionUtils.isStableVersion("1.0.0-beta"));
        assertFalse(OpenclaudeVersionUtils.isStableVersion("1.0.0+build"));
        assertFalse(OpenclaudeVersionUtils.isStableVersion("latest"));
        assertFalse(OpenclaudeVersionUtils.isStableVersion(null));
        assertFalse(OpenclaudeVersionUtils.isStableVersion(""));
    }

    @Test
    public void testSortAndLimit_deduplicates() {
        List<String> input = Arrays.asList("1.0.0", "1.0.0", "1.1.0");
        List<String> result = OpenclaudeVersionUtils.sortAndLimit(input);
        assertEquals(2, result.size());
        assertEquals("1.1.0", result.get(0));
    }

    @Test
    public void testSortAndLimit_latestFirst() {
        List<String> input = Arrays.asList("1.0.0", "latest", "2.0.0");
        List<String> result = OpenclaudeVersionUtils.sortAndLimit(input);
        assertEquals("latest", result.get(0));
        assertEquals("2.0.0", result.get(1));
        assertEquals("1.0.0", result.get(2));
    }

    @Test
    public void testCompareDesc_ordering() {
        assertTrue(OpenclaudeVersionUtils.compareDesc("2.0.0", "1.0.0") < 0);
        assertTrue(OpenclaudeVersionUtils.compareDesc("1.0.0", "2.0.0") > 0);
        assertEquals(0, OpenclaudeVersionUtils.compareDesc("1.0.0", "1.0.0"));
        assertTrue(OpenclaudeVersionUtils.compareDesc("latest", "1.0.0") < 0);
        assertTrue(OpenclaudeVersionUtils.compareDesc("1.0.0", "latest") > 0);
    }

    @Test
    public void testRecommendOpenclaudeOldSpaceMb_clampsAndScales() {
        assertEquals(1536, OpenclaudeVersionUtils.recommendOpenclaudeOldSpaceMb(0));
        assertEquals(1536, OpenclaudeVersionUtils.recommendOpenclaudeOldSpaceMb(4096));
        assertEquals(2048, OpenclaudeVersionUtils.recommendOpenclaudeOldSpaceMb(6144));
        assertEquals(2048, OpenclaudeVersionUtils.recommendOpenclaudeOldSpaceMb(8192));
        assertEquals(2560, OpenclaudeVersionUtils.recommendOpenclaudeOldSpaceMb(10240));
        assertEquals(3072, OpenclaudeVersionUtils.recommendOpenclaudeOldSpaceMb(12288));
        assertEquals(4096, OpenclaudeVersionUtils.recommendOpenclaudeOldSpaceMb(32768));
    }

    @Test
    public void testBuildOpenclaudeNodeOptions_addsDnsAndHeapLimit() {
        String options = OpenclaudeVersionUtils.buildOpenclaudeNodeOptions("", 8192);

        assertTrue(options.contains("--dns-result-order=ipv4first"));
        assertTrue(options.contains("--max-old-space-size=2048"));
    }

    @Test
    public void testBuildOpenclaudeNodeOptions_preservesExplicitHeapLimit() {
        String options = OpenclaudeVersionUtils.buildOpenclaudeNodeOptions(
            "--trace-warnings --max-old-space-size=6144",
            8192
        );

        assertTrue(options.contains("--dns-result-order=ipv4first"));
        assertTrue(options.contains("--trace-warnings"));
        assertTrue(options.contains("--max-old-space-size=6144"));
        assertFalse(options.contains("--max-old-space-size=2048"));
    }

    @Test
    public void testBuildNodeOptionsExportCommand_containsHeapSetup() {
        String script = OpenclaudeVersionUtils.buildNodeOptionsExportCommand();

        assertTrue(script.contains("KODA_OPENCLAUDE_MAX_OLD_SPACE_MB"));
        assertTrue(script.contains("--dns-result-order=ipv4first"));
        assertTrue(script.contains("--max-old-space-size="));
    }

    @Test
    public void testBuildNodeOptionsExportCommand_withPrecomputedOldSpace() {
        String script = OpenclaudeVersionUtils.buildNodeOptionsExportCommand(2048);

        assertTrue(script.startsWith("export KODA_OPENCLAUDE_DEFAULT_MAX_OLD_SPACE_MB=2048\n"));
        assertTrue(script.contains(
            "old_space_mb=\"${KODA_OPENCLAUDE_MAX_OLD_SPACE_MB:-${KODA_OPENCLAUDE_DEFAULT_MAX_OLD_SPACE_MB:-}}\""));
        assertTrue(script.contains("--dns-result-order=ipv4first"));
        assertTrue(script.contains("--max-old-space-size="));
    }

    @Test
    public void testBuildNpmInstallCommand_withPrecomputedOldSpace() {
        String command = OpenclaudeVersionUtils.buildNpmInstallCommand("openclaude@latest", 3072);

        assertTrue(command.contains("export KODA_OPENCLAUDE_DEFAULT_MAX_OLD_SPACE_MB=3072\n"));
        assertTrue(command.contains("npm install -g 'openclaude@latest' --ignore-scripts --force"));
    }

    @Test
    public void testBuildNpmAwareCommand_injectsRegistryResolver() {
        String command = OpenclaudeVersionUtils.buildNpmAwareCommand(
            "openclaude plugins install @sliverp/qqbot@latest");

        assertTrue(command.contains("koda_resolve_npm_registry()"));
        assertTrue(command.contains("export NPM_CONFIG_REGISTRY"));
        assertTrue(command.contains("--dns-result-order=ipv4first"));
        assertTrue(command.endsWith("openclaude plugins install @sliverp/qqbot@latest"));
    }

    @Test
    public void testBuildNpmAwareCommand_withPrecomputedOldSpace() {
        String command = OpenclaudeVersionUtils.buildNpmAwareCommand("npm install -g sharp@0.34.5", 2560);

        assertTrue(command.contains("export KODA_OPENCLAUDE_DEFAULT_MAX_OLD_SPACE_MB=2560\n"));
        assertTrue(command.contains("export NPM_CONFIG_REGISTRY"));
        assertTrue(command.endsWith("npm install -g sharp@0.34.5"));
    }

    @Test
    public void testBuildModelListCommand() {
        assertEquals(OpenclaudeVersionUtils.MODEL_LIST_COMMAND, OpenclaudeVersionUtils.buildModelListCommand());
        assertEquals(
            "KODA_TRACE_NPM_REGISTRY=1 " + OpenclaudeVersionUtils.MODEL_LIST_COMMAND,
            OpenclaudeVersionUtils.buildModelListCommand(true)
        );
    }

    @Test
    public void testBuildNpmAwareCommand_prefersMirrorForCnExit() {
        String command = OpenclaudeVersionUtils.buildNpmAwareCommand("npm install -g openclaude@latest");

        assertTrue(command.contains("country=\"$(curl -m 2 -fsSL https://ipinfo.io/country"));
        assertTrue(command.contains("if [ \"$country\" = \"CN\" ] && [ \"$npmmirror_probe\" = \"200\" ]; then"));
        assertTrue(command.contains("resolved=\"$cn_registry\""));
        assertFalse(command.contains("tencent_registry"));
    }

    @Test
    public void testBuildNpmAwareCommand_keepsRegistryCache() {
        String command = OpenclaudeVersionUtils.buildNpmAwareCommand("npm view openclaude version");

        assertTrue(command.contains("cache_file=\"$HOME/.koda_npm_registry_cache\""));
        assertTrue(command.contains("cache_ttl_seconds=86400"));
        assertTrue(command.contains(
            "    } > \"$cache_file\"\n"
                + "  fi\n"
                + "\n"
                + "  if [ -z \"$resolved\" ]; then\n"));
        assertFalse(command.contains("koda_npm_trace()"));
    }

    @Test
    public void testBuildOpenclaudeWrapperBody_addsFastVersionPath() {
        String script = OpenclaudeVersionUtils.buildOpenclaudeWrapperBody(
            "$RUNTIME_ROOT/node_modules/openclaude",
            "$RUNTIME_ROOT/node_modules:$PREFIX/lib/node_modules",
            2048
        );

        assertTrue(script.contains("if [ \"$#\" -eq 1 ] && [ \"$1\" = \"version\" ]; then"));
        assertTrue(script.contains("set -- --version"));
        assertTrue(script.contains("case \"$1\" in\n  -V|--version)"));
        assertTrue(script.contains("printf 'OpenClaude %s\\n' \"$VERSION\""));
        assertTrue(script.contains("PACKAGE_ROOT=\"$RUNTIME_ROOT/node_modules/openclaude\""));
        assertTrue(script.contains("export NODE_PATH=\"$RUNTIME_ROOT/node_modules:$PREFIX/lib/node_modules\""));
        assertTrue(script.contains("exec \"$PREFIX/bin/termux-chroot\" \"$PREFIX/bin/node\" \"$ENTRY\" \"$@\""));
    }
}
