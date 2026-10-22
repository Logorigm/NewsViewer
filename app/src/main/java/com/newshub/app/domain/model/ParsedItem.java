package com.newshub.app.domain.model;

import java.util.ArrayList;
import java.util.List;

/** One publication extracted from a feed or a Telegram preview page, before it is stored. */
public final class ParsedItem {
    /** Stable identifier inside the source (guid, Atom id, Telegram post id or link). */
    public String guid = "";
    public String title = "";
    /** Link to the original publication. */
    public String link = "";
    /** Plain-text excerpt (HTML stripped, length-limited). */
    public String summary = "";
    public String imageUrl;
    public String author;
    /** Epoch millis (UTC), or 0 when the feed did not provide a parsable date. */
    public long publishedAt;
    /** Tags/categories provided by the source itself. */
    public final List<String> tags = new ArrayList<>();

    @Override
    public String toString() {
        return "ParsedItem{" + title + " | " + link + "}";
    }
}
