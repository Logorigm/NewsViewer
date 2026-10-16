package com.newshub.app.domain.text;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Text normalization shared by search, classification and duplicate detection. */
public final class TextNormalizer {

    private TextNormalizer() {
    }

    /** Russian and English function words that carry no topic information. */
    public static final Set<String> STOPWORDS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "и", "в", "во", "не", "что", "он", "на", "я", "с", "со", "как", "а", "то", "все", "она",
            "так", "его", "но", "да", "ты", "к", "у", "же", "вы", "за", "бы", "по", "только", "ее",
            "мне", "было", "вот", "от", "меня", "еще", "нет", "о", "об", "из", "ему", "теперь", "когда",
            "даже", "ну", "вдруг", "ли", "если", "уже", "или", "ни", "быть", "был", "него", "до",
            "вас", "нибудь", "опять", "уж", "вам", "ведь", "там", "потом", "себя", "ничего", "ей",
            "может", "они", "тут", "где", "есть", "надо", "ней", "для", "мы", "тебя", "их", "чем",
            "была", "сам", "чтоб", "без", "будто", "чего", "раз", "тоже", "себе", "под", "будет",
            "ж", "тогда", "кто", "этот", "того", "потому", "этого", "какой", "совсем", "ним",
            "здесь", "этом", "один", "почти", "мой", "тем", "чтобы", "нее", "сейчас", "были",
            "куда", "зачем", "всех", "никогда", "можно", "при", "наконец", "два", "другой", "хоть",
            "после", "над", "больше", "тот", "через", "эти", "нас", "про", "всего", "них", "какая",
            "много", "разве", "три", "эту", "моя", "впрочем", "хорошо", "свою", "этой", "перед",
            "иногда", "лучше", "чуть", "том", "нельзя", "такой", "им", "более", "всегда", "конечно",
            "всю", "между", "это", "эта", "эти", "также", "года", "году", "год", "стал", "стало",
            "стала", "стали", "заявил", "заявила", "сообщил", "сообщила", "сообщили", "рассказал",
            "рассказала", "назвал", "назвала", "новый", "новая", "новое", "новые", "который",
            "которая", "которое", "которые", "которых", "где", "во", "из-за", "ради",
            "the", "a", "an", "of", "to", "in", "on", "for", "and", "or", "is", "are", "was", "were",
            "be", "with", "by", "at", "from", "as", "it", "its", "this", "that", "new", "how", "why"
    )));

    private static final Pattern NUMBER = Pattern.compile("\\d+(?:[.,]\\d+)?");

    private static final Pattern LEADING_LABEL = Pattern.compile(
            "^(?:молния|срочно|эксклюзив|breaking|важно|видео|фото|онлайн|live)\\s*[:|—–-]\\s*",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    /**
     * Lowercases, folds "ё" to "е", replaces every non letter/digit with a space and collapses
     * whitespace. The result contains only letters, digits and single spaces.
     */
    public static String normalize(String text) {
        if (text == null || text.isEmpty()) return "";
        String lower = text.toLowerCase(Locale.ROOT).replace('ё', 'е');
        StringBuilder sb = new StringBuilder(lower.length());
        boolean lastSpace = true;
        for (int i = 0; i < lower.length(); i++) {
            char ch = lower.charAt(i);
            if (Character.isLetterOrDigit(ch)) {
                sb.append(ch);
                lastSpace = false;
            } else if (!lastSpace) {
                sb.append(' ');
                lastSpace = true;
            }
        }
        int len = sb.length();
        if (len > 0 && sb.charAt(len - 1) == ' ') sb.setLength(len - 1);
        return sb.toString();
    }

    public static List<String> tokens(String text) {
        String norm = normalize(text);
        if (norm.isEmpty()) return Collections.emptyList();
        return Arrays.asList(norm.split(" "));
    }

    /** Stems of meaningful words (stopwords and one-letter tokens removed), in text order. */
    public static List<String> contentStems(String text) {
        List<String> out = new ArrayList<>();
        for (String t : tokens(text)) {
            if (STOPWORDS.contains(t)) continue;
            if (t.length() < 2 && !isDigits(t)) continue;
            out.add(RussianStemmer.stem(t));
        }
        return out;
    }

    public static Set<String> stemSet(String text) {
        return new LinkedHashSet<>(contentStems(text));
    }

    /** Numbers mentioned in the text, with "," normalized to "." (e.g. "7,5" -> "7.5"). */
    public static Set<String> numbers(String text) {
        Set<String> out = new LinkedHashSet<>();
        if (text == null) return out;
        Matcher m = NUMBER.matcher(text);
        while (m.find()) out.add(m.group().replace(',', '.'));
        return out;
    }

    /**
     * Proper-noun-like words of a headline: capitalized words that are not the first word,
     * acronyms anywhere, and a capitalized Latin-script first word (in Russian headlines that is
     * practically always a brand or a name: "Nvidia выпустила…"). Returned as stems.
     */
    public static Set<String> entities(String title) {
        Set<String> out = new LinkedHashSet<>();
        if (title == null) return out;
        String[] words = title.split("[^\\p{L}\\p{Nd}-]+");
        boolean first = true;
        for (String w : words) {
            if (w.isEmpty()) continue;
            boolean acronym = w.length() >= 2 && w.equals(w.toUpperCase(Locale.ROOT))
                    && !w.equals(w.toLowerCase(Locale.ROOT));
            boolean capitalized = Character.isUpperCase(w.charAt(0));
            boolean latinFirst = first && capitalized && w.charAt(0) < 0x0250;
            if (acronym || latinFirst || (capitalized && !first)) {
                String n = normalize(w);
                if (!n.isEmpty() && !STOPWORDS.contains(n)) out.add(RussianStemmer.stem(n.replace(" ", "")));
            }
            first = false;
        }
        return out;
    }

    /** Removes service labels like "Молния:" or "Срочно |" at the beginning of a headline. */
    public static String stripLabels(String title) {
        if (title == null) return "";
        String t = title.trim();
        String prev;
        do {
            prev = t;
            t = LEADING_LABEL.matcher(t).replaceFirst("").trim();
        } while (!t.equals(prev));
        return t;
    }

    /** Collapses all whitespace (including non-breaking spaces) to single spaces. */
    public static String collapseWhitespace(String text) {
        if (text == null) return "";
        return text.replace(' ', ' ').replaceAll("\\s+", " ").trim();
    }

    /** Lowercased searchable representation of an article. */
    public static String searchText(String title, String summary) {
        return normalize((title == null ? "" : title) + " " + (summary == null ? "" : summary));
    }

    /** Stemmed search terms for a user query (used as substring patterns). */
    public static List<String> searchTerms(String query) {
        List<String> out = new ArrayList<>();
        for (String t : tokens(query)) {
            String term = t.length() > 4 ? RussianStemmer.stem(t) : t;
            if (!term.isEmpty() && !out.contains(term)) out.add(term);
        }
        return out;
    }

    static boolean isDigits(String s) {
        if (s.isEmpty()) return false;
        for (int i = 0; i < s.length(); i++) {
            if (!Character.isDigit(s.charAt(i))) return false;
        }
        return true;
    }
}
