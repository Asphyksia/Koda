package dev.koda;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class BundledOpenclaudeUtilsTest {

    @Test
    public void testParseManifest_returnsExpectedFields() {
        String manifest =
            "version=2026.3.13\n" +
            "installSpec=openclaude@2026.3.13\n" +
            "runtimeArchive=openclaude-runtime.tar\n" +
            "qqbotDir=qqbot\n";

        BundledOpenclaudeUtils.Manifest parsed = BundledOpenclaudeUtils.parseManifest(manifest);

        assertNotNull(parsed);
        assertEquals("2026.3.13", parsed.version);
        assertEquals("openclaude@2026.3.13", parsed.installSpec);
        assertEquals("openclaude-runtime.tar", parsed.runtimeArchive);
        assertEquals("qqbot", parsed.qqbotDir);
    }

    @Test
    public void testResolvePreferredInstallSpec_prefersBundledVersionForLatest() {
        BundledOpenclaudeUtils.Manifest manifest = new BundledOpenclaudeUtils.Manifest(
            "2026.3.13",
            "openclaude@2026.3.13",
            "openclaude-runtime.tar",
            "qqbot"
        );

        assertEquals(
            "openclaude@2026.3.13",
            BundledOpenclaudeUtils.resolvePreferredInstallSpec("openclaude@latest", manifest)
        );
        assertEquals(
            "openclaude@2026.3.13",
            BundledOpenclaudeUtils.resolvePreferredInstallSpec("latest", manifest)
        );
    }

    @Test
    public void testResolvePreferredInstallSpec_keepsExplicitVersion() {
        BundledOpenclaudeUtils.Manifest manifest = new BundledOpenclaudeUtils.Manifest(
            "2026.3.13",
            "openclaude@2026.3.13",
            "openclaude-runtime.tar",
            "qqbot"
        );

        assertEquals(
            "openclaude@2026.2.6",
            BundledOpenclaudeUtils.resolvePreferredInstallSpec("openclaude@2026.2.6", manifest)
        );
    }

    @Test
    public void testShouldDisableVersionManagement_whenBundleManifestPresent() {
        BundledOpenclaudeUtils.Manifest manifest = new BundledOpenclaudeUtils.Manifest(
            "2026.3.13",
            "openclaude@2026.3.13",
            "openclaude-runtime.tar",
            "qqbot"
        );

        assertFalse(BundledOpenclaudeUtils.shouldDisableVersionManagement(manifest));
    }

    @Test
    public void testShouldDisableVersionManagement_withoutBundleManifest() {
        assertFalse(BundledOpenclaudeUtils.shouldDisableVersionManagement(null));
    }

    @Test
    public void testShouldDisableUpdateManagement_whenBundleManifestPresent() {
        BundledOpenclaudeUtils.Manifest manifest = new BundledOpenclaudeUtils.Manifest(
            "2026.3.13",
            "openclaude@2026.3.13",
            "openclaude-runtime.tar",
            "qqbot"
        );

        assertFalse(BundledOpenclaudeUtils.shouldDisableUpdateManagement(manifest));
    }

    @Test
    public void testShouldDisableUpdateManagement_withoutBundleManifest() {
        assertFalse(BundledOpenclaudeUtils.shouldDisableUpdateManagement((BundledOpenclaudeUtils.Manifest) null));
    }

    @Test
    public void testParseManifest_missingVersionReturnsNull() {
        String manifest =
            "installSpec=openclaude@2026.3.13\n" +
            "runtimeArchive=openclaude-runtime.tar\n";

        assertNull(BundledOpenclaudeUtils.parseManifest(manifest));
    }

    @Test
    public void testQqbotSourceDir_pointsToOfflineBundleRoot() {
        assertEquals(
            "/data/data/dev.koda/files/usr/share/koda/offline-openclaude/qqbot",
            BundledOpenclaudeUtils.STAGED_QQBOT_PLUGIN_SOURCE_DIR
        );
    }

    @Test
    public void testBuildOfflineQqbotInstallCommand_usesOfflineScript() {
        assertEquals(
            "bash '/data/data/dev.koda/files/usr/share/koda/install-qqbot-offline.sh'",
            BundledOpenclaudeUtils.buildOfflineQqbotInstallCommand()
        );
    }

    @Test
    public void testBuildOfflineQqbotInstallScriptBody_copiesBundledPluginWithoutNpm() {
        String script = BundledOpenclaudeUtils.buildOfflineQqbotInstallScriptBody();

        assertTrue(script.contains("QQBOT_SOURCE=\"/data/data/dev.koda/files/usr/share/koda/offline-openclaude/qqbot\""));
        assertTrue(script.contains("QQBOT_TARGET=\"/data/data/dev.koda/files/home/.openclaude/extensions/qqbot\""));
        assertTrue(script.contains("CONFIG_PATH=\"/data/data/dev.koda/files/home/.openclaude/openclaude.json\""));
        assertTrue(script.contains("cp -R \"$QQBOT_SOURCE/.\" \"$QQBOT_TARGET/\""));
        assertTrue(script.contains("config.plugins.entries.qqbot"));
        assertTrue(script.contains("QQBOT_OFFLINE_INSTALL_COMPLETE"));
        assertFalse(script.contains("npm install"));
        assertFalse(script.contains("openclaude plugins install"));
    }
}
