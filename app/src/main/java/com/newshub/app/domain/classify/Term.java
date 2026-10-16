package com.newshub.app.domain.classify;

import com.newshub.app.domain.text.TextNormalizer;

import java.util.ArrayList;
import java.util.List;

/**
 * A keyword rule. A term is one or more words; each word matches a token by prefix ("выбор"
 * matches "выборы", "выборах"), or exactly when written with a trailing "$" ("мид$" does not
 * match "мидии"). Multi-word terms must match consecutive tokens.
 */
public final class Term {

    public final String display;
    public final int weight;
    private final String[] words;
    private final boolean[] exact;

    private Term(String display, String[] words, boolean[] exact, int weight) {
        this.display = display;
        this.words = words;
        this.exact = exact;
        this.weight = weight;
    }

    /** @return the term, or null when the spec contains no letters or digits. */
    public static Term of(String spec, int weight) {
        if (spec == null) return null;
        List<String> ws = new ArrayList<>();
        List<Boolean> ex = new ArrayList<>();
        for (String raw : spec.trim().split("\\s+")) {
            boolean isExact = raw.endsWith("$");
            String w = TextNormalizer.normalize(isExact ? raw.substring(0, raw.length() - 1) : raw);
            if (w.isEmpty()) continue;
            for (String part : w.split(" ")) {
                ws.add(part);
                ex.add(isExact);
            }
        }
        if (ws.isEmpty()) return null;
        boolean[] exactArr = new boolean[ex.size()];
        for (int i = 0; i < exactArr.length; i++) exactArr[i] = ex.get(i);
        return new Term(spec.replace("$", "").trim(), ws.toArray(new String[0]), exactArr, weight);
    }

    public boolean matches(List<String> tokens) {
        int n = words.length;
        for (int i = 0; i + n <= tokens.size(); i++) {
            boolean ok = true;
            for (int j = 0; j < n; j++) {
                String tok = tokens.get(i + j);
                if (exact[j] ? !tok.equals(words[j]) : !tok.startsWith(words[j])) {
                    ok = false;
                    break;
                }
            }
            if (ok) return true;
        }
        return false;
    }

    public static List<Term> list(int weight, String... specs) {
        List<Term> out = new ArrayList<>();
        for (String s : specs) {
            Term t = of(s, weight);
            if (t != null) out.add(t);
        }
        return out;
    }

    /** Parses user input "слово, другое слово; третье" into terms of the given weight. */
    public static List<Term> parseUserList(String input, int weight) {
        List<Term> out = new ArrayList<>();
        if (input == null) return out;
        for (String part : input.split("[,;\\n]")) {
            Term t = of(part, weight);
            if (t != null) out.add(t);
        }
        return out;
    }

    @Override
    public String toString() {
        return display;
    }
}
