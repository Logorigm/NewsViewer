package com.newshub.app.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.newshub.app.data.db.entity.SourceEntity;
import com.newshub.app.data.source.CatalogSource;
import com.newshub.app.data.source.SourceCatalogParser;
import com.newshub.app.domain.SourceType;
import com.newshub.app.domain.VerificationLevel;
import com.newshub.app.domain.parse.TelegramPreviewParser;
import com.newshub.app.domain.text.UrlNormalizer;

import org.json.JSONException;
import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SourceCatalogTest {

    private static String catalogJson() throws Exception {
        File f = new File("src/main/assets/sources.json");
        if (!f.exists()) f = new File("app/src/main/assets/sources.json");
        return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
    }

    @Test
    public void builtInCatalogIsValid() throws Exception {
        List<CatalogSource> sources = SourceCatalogParser.parse(catalogJson());
        assertTrue(sources.size() >= 25);
        Set<String> names = new HashSet<>();
        for (CatalogSource s : sources) {
            assertTrue("duplicate name " + s.name, names.add(s.name));
            VerificationLevel level = VerificationLevel.valueOf(s.verification);
            SourceType type = SourceType.valueOf(s.type);
            if (level == VerificationLevel.UNVERIFIED) {
                // nothing is invented: unverified sources have no address and are off
                assertTrue(s.id, s.address.isEmpty());
                assertFalse(s.id, s.enabledByDefault);
            } else {
                assertFalse(s.id, s.address.isEmpty());
                if (type == SourceType.TELEGRAM) {
                    assertNotNull(s.id, TelegramPreviewParser.normalizeHandle(s.address));
                } else {
                    assertTrue(s.id, UrlNormalizer.isHttpsUrl(s.address));
                }
            }
            SourceEntity e = s.toEntity(0, 0);
            assertEquals(!s.address.isEmpty() && s.enabledByDefault, e.enabled);
            assertTrue(e.builtIn);
        }
    }

    @Test
    public void duplicateAddressIsRejected() {
        String json = "{\"sources\":["
                + "{\"id\":\"a\",\"name\":\"A\",\"type\":\"RSS\",\"address\":\"https://x.ru/rss\",\"category\":\"tech\",\"verification\":\"KNOWN\"},"
                + "{\"id\":\"b\",\"name\":\"B\",\"type\":\"RSS\",\"address\":\"https://www.x.ru/rss/\",\"category\":\"science\",\"verification\":\"KNOWN\"}]}";
        try {
            SourceCatalogParser.parse(json);
            fail("the same feed in two categories must be rejected");
        } catch (JSONException expected) {
            assertTrue(expected.getMessage().contains("Duplicate"));
        }
    }

    @Test
    public void unknownCategoryIsRejected() {
        String json = "{\"sources\":[{\"id\":\"a\",\"name\":\"A\",\"type\":\"RSS\",\"address\":\"https://x.ru/rss\","
                + "\"category\":\"politics\",\"verification\":\"KNOWN\"}]}";
        try {
            SourceCatalogParser.parse(json);
            fail();
        } catch (JSONException expected) {
            // ok
        }
    }

    @Test
    public void telegramAddressKeyIgnoresFormat() {
        assertEquals(SourceCatalogParser.addressKey("TELEGRAM", "@Mash"),
                SourceCatalogParser.addressKey("TELEGRAM", "https://t.me/s/mash"));
    }
}
