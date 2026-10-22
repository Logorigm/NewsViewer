package com.newshub.app.data.source;

import com.newshub.app.domain.Category;
import com.newshub.app.domain.SourceType;
import com.newshub.app.domain.VerificationLevel;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Parses and validates the built-in source catalog. Sources live in a JSON config so the list can
 * be changed without touching code. Validation rejects duplicate ids and duplicate addresses so a
 * source cannot appear twice (e.g. in two categories).
 */
public final class SourceCatalogParser {

    private SourceCatalogParser() {
    }

    public static List<CatalogSource> parse(String json) throws JSONException {
        JSONObject root = new JSONObject(json);
        JSONArray arr = root.getJSONArray("sources");
        List<CatalogSource> out = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        Set<String> addresses = new HashSet<>();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.getJSONObject(i);
            CatalogSource s = new CatalogSource();
            s.id = o.getString("id").trim();
            s.name = o.getString("name").trim();
            s.type = SourceType.parse(o.optString("type", "RSS"), SourceType.RSS).name();
            s.address = o.optString("address", "").trim();
            s.homepage = o.optString("homepage", "").trim();
            if (s.homepage.isEmpty()) s.homepage = null;
            Category category = Category.fromKey(o.optString("category"), null);
            if (category == null || category == Category.MAIN) {
                throw new JSONException("Source " + s.id + ": unknown category " + o.optString("category"));
            }
            s.category = category.key;
            s.verification = VerificationLevel.parse(o.optString("verification")).name();
            s.enabledByDefault = o.optBoolean("enabled", true);
            s.note = o.optString("note", "").trim();
            if (s.note.isEmpty()) s.note = null;
            if (s.id.isEmpty() || s.name.isEmpty()) throw new JSONException("Source #" + i + " has no id or name");
            if (!ids.add(s.id)) throw new JSONException("Duplicate source id " + s.id);
            if (!s.address.isEmpty() && !addresses.add(addressKey(s.type, s.address))) {
                throw new JSONException("Duplicate source address " + s.address);
            }
            out.add(s);
        }
        return out;
    }

    /** Key that identifies the same source regardless of letter case or trailing slashes. */
    public static String addressKey(String type, String address) {
        String a = address.trim().toLowerCase(Locale.ROOT);
        if (SourceType.TELEGRAM.name().equals(type)) {
            String handle = com.newshub.app.domain.parse.TelegramPreviewParser.normalizeHandle(address);
            return "tg:" + (handle == null ? a : handle.toLowerCase(Locale.ROOT));
        }
        a = a.replaceFirst("^https?://", "").replaceFirst("^www\\.", "");
        while (a.endsWith("/")) a = a.substring(0, a.length() - 1);
        return "web:" + a;
    }
}
