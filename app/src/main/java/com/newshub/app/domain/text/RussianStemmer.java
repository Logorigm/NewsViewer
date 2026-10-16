package com.newshub.app.domain.text;

/**
 * Deliberately light stemmer: strips the most common Russian inflection endings and caps the
 * stem length. It is used only to compare headlines and to match search terms, where a cheap and
 * predictable normalization is more valuable than linguistic precision.
 */
public final class RussianStemmer {

    private RussianStemmer() {
    }

    private static final String[] ENDINGS = {
            "ировать", "ениями", "ениях", "ением", "ование", "ования",
            "иями", "ения", "ение", "ений", "ями", "ами", "ого", "его", "ому", "ему", "ыми", "ими",
            "ией", "ила", "ило", "или", "ыла", "ала", "яла", "ете", "ешь", "ают", "яют", "уют",
            "ия", "ии", "ий", "ый", "ой", "ая", "яя", "ое", "ее", "ые", "ие", "ую", "юю",
            "ом", "ем", "ам", "ям", "ах", "ях", "ов", "ев", "ей", "ет", "ит", "ут", "ют", "ат", "ят",
            "ил", "ть", "ла", "ли", "ло",
            "а", "я", "о", "е", "ы", "и", "у", "ю", "ь", "й"
    };

    private static final int MIN_STEM = 3;
    private static final int MAX_STEM = 7;

    public static String stem(String word) {
        if (word == null) return "";
        String w = word;
        if (w.length() <= MIN_STEM) return w;
        if (!isCyrillic(w)) return latinStem(w);
        for (String ending : ENDINGS) {
            if (w.endsWith(ending) && w.length() - ending.length() >= MIN_STEM) {
                w = w.substring(0, w.length() - ending.length());
                break;
            }
        }
        if (w.length() > MAX_STEM) w = w.substring(0, MAX_STEM);
        return w;
    }

    private static String latinStem(String w) {
        if (TextNormalizer.isDigits(w)) return w;
        if (w.length() > 4 && w.endsWith("s") && !w.endsWith("ss")) w = w.substring(0, w.length() - 1);
        if (w.length() > 9) w = w.substring(0, 9);
        return w;
    }

    private static boolean isCyrillic(String w) {
        for (int i = 0; i < w.length(); i++) {
            Character.UnicodeBlock block = Character.UnicodeBlock.of(w.charAt(i));
            if (block == Character.UnicodeBlock.CYRILLIC) return true;
        }
        return false;
    }
}
