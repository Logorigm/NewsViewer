package com.newshub.app.domain.model;

import java.util.ArrayList;
import java.util.List;

/** Result of parsing one feed document. */
public final class ParsedFeed {
    public String title = "";
    public String format = "";
    public final List<ParsedItem> items = new ArrayList<>();
}
