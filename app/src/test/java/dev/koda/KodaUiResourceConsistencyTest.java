package dev.koda;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.File;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import javax.xml.parsers.DocumentBuilderFactory;

public class KodaUiResourceConsistencyTest {

    private static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";
    private static final File PROJECT_ROOT = findProjectRoot();

    @Test
    public void zhStringsIncludeNewDashboardAndSettingsKeys() throws Exception {
        Set<String> zhKeys = loadStringNames("app/src/main/res/values-zh-rCN/strings.xml");
        for (String key : Arrays.asList(
            "koda_check_updates",
            "koda_view_log_title",
            "koda_view_log_desc",
            "koda_open_web_ui_title",
            "koda_open_web_ui_desc",
            "koda_automation_panel_desc",
            "koda_tools",
            "koda_data_recovery",
            "koda_data_recovery_desc",
            "koda_set",
            "koda_terminal_title",
            "koda_terminal_desc",
            "koda_change_version",
            "koda_check_koda_update",
            "koda_app_version_label",
            "koda_openclaude_version_label"
        )) {
            assertTrue("Missing zh-CN string: " + key, zhKeys.contains(key));
        }
    }

    @Test
    public void dashboardPrimaryControlsUseButtonElements() throws Exception {
        Document document = parseXml("app/src/main/res/layout/activity_koda_dashboard.xml");

        assertEquals("TextView", findElementById(document, "btn_check_openclaude_update").getTagName());
        assertEquals("LinearLayout", findElementById(document, "btn_start").getTagName());
        assertEquals("LinearLayout", findElementById(document, "btn_stop").getTagName());
        assertEquals("LinearLayout", findElementById(document, "btn_restart").getTagName());
        assertEquals("ImageView", findElementById(document, "btn_start_icon").getTagName());
        assertEquals("ImageView", findElementById(document, "btn_stop_icon").getTagName());
        assertEquals("ImageView", findElementById(document, "btn_restart_icon").getTagName());
    }

    @Test
    public void settingsPrimaryActionsAvoidFixedButtonWidths() throws Exception {
        Document document = parseXml("app/src/main/res/layout/activity_koda_settings.xml");

        assertFalse(hasFixedWidth(findElementById(document, "btn_change_openclaude_version")));
        assertFalse(hasFixedWidth(findElementById(document, "btn_check_koda_update")));
    }

    @Test
    public void settingsExternalRowsUseOpenInNewIcons() throws Exception {
        Document document = parseXml("app/src/main/res/layout/activity_koda_settings.xml");

        assertEquals("ImageView", findElementById(document, "settings_website_external_icon").getTagName());
        assertEquals("ImageView", findElementById(document, "settings_docs_external_icon").getTagName());
        assertEquals("ImageView", findElementById(document, "settings_x_external_icon").getTagName());
        assertEquals("ImageView", findElementById(document, "settings_discord_external_icon").getTagName());
    }

    @Test
    public void resourceFilesDoNotKeepKnownUnusedStrings() throws Exception {
        Set<String> baseKeys = loadStringNames("app/src/main/res/values/strings.xml");
        Set<String> zhKeys = loadStringNames("app/src/main/res/values-zh-rCN/strings.xml");

        for (String key : Arrays.asList(
            "koda_gateway_starting",
            "koda_open_automation_panel",
            "koda_backup_restore",
            "koda_open_terminal",
            "koda_checking_updates",
            "koda_versions",
            "koda_row_chevron",
            "koda_channel_id",
            "koda_openclaude_web_update_title",
            "koda_copy_label",
            "koda_no_browser_app_found"
        )) {
            assertFalse("Unused English string still present: " + key, baseKeys.contains(key));
            assertFalse("Unused zh-CN string still present: " + key, zhKeys.contains(key));
        }
    }

    @Test
    public void zhGatewayStatusStringsKeepGatewayTerm() throws Exception {
        Document document = parseXml("app/src/main/res/values-zh-rCN/strings.xml");

        assertEquals("启动 Gateway 失败", findStringValue(document, "koda_gateway_start_failed"));
        assertEquals("Gateway 已启动", findStringValue(document, "koda_gateway_started"));
        assertEquals("Gateway 已重启", findStringValue(document, "koda_gateway_restarted"));
        assertEquals("Gateway 已成功重启", findStringValue(document, "koda_gateway_restarted_successfully"));
        assertEquals("Gateway 重启失败", findStringValue(document, "koda_gateway_restart_failed"));
        assertEquals("正在重启 Gateway", findStringValue(document, "koda_gateway_restarting"));
        assertEquals("正在为新模型重启 Gateway", findStringValue(document, "koda_gateway_restarting_with_new_model"));
        assertEquals("停止 Gateway 失败", findStringValue(document, "koda_gateway_stop_failed"));
        assertEquals("Gateway 已停止", findStringValue(document, "koda_gateway_stopped_toast"));
    }

    private static boolean hasFixedWidth(Element element) {
        String width = element.getAttributeNS(ANDROID_NS, "layout_width");
        return width != null && width.endsWith("dp");
    }

    private static Set<String> loadStringNames(String relativePath) throws Exception {
        Document document = parseXml(relativePath);
        Set<String> keys = new HashSet<>();
        NodeList strings = document.getElementsByTagName("string");
        for (int i = 0; i < strings.getLength(); i++) {
            Element element = (Element) strings.item(i);
            keys.add(element.getAttribute("name"));
        }
        return keys;
    }

    private static Element findElementById(Document document, String id) {
        NodeList allElements = document.getElementsByTagName("*");
        for (int i = 0; i < allElements.getLength(); i++) {
            Element element = (Element) allElements.item(i);
            String elementId = element.getAttributeNS(ANDROID_NS, "id");
            if (("@+id/" + id).equals(elementId) || ("@id/" + id).equals(elementId)) {
                return element;
            }
        }
        throw new AssertionError("Could not find view with id " + id);
    }

    private static String findStringValue(Document document, String name) {
        NodeList strings = document.getElementsByTagName("string");
        for (int i = 0; i < strings.getLength(); i++) {
            Element element = (Element) strings.item(i);
            if (name.equals(element.getAttribute("name"))) {
                return element.getTextContent().trim();
            }
        }
        throw new AssertionError("Could not find string " + name);
    }

    private static Document parseXml(String relativePath) throws Exception {
        File file = new File(PROJECT_ROOT, relativePath);
        if (!file.isFile()) {
            throw new java.io.FileNotFoundException(file.getAbsolutePath());
        }
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        return factory.newDocumentBuilder().parse(file);
    }

    private static File findProjectRoot() {
        File current = new File(System.getProperty("user.dir"));
        while (current != null) {
            if (new File(current, "app/src/main/res").isDirectory()) {
                return current;
            }
            current = current.getParentFile();
        }
        throw new IllegalStateException("Could not locate project root from " + System.getProperty("user.dir"));
    }
}
