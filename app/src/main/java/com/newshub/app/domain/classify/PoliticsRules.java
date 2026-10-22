package com.newshub.app.domain.classify;

import com.newshub.app.domain.text.TextNormalizer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Transparent, keyword-based politics detection.
 *
 * <p>Every rule has a weight (1 = weak hint, 3 = clearly political). A match in the headline
 * counts double. A publication is political when the total reaches the sensitivity threshold.
 * Weak words such as "правительство", "губернатор" or "президент" alone never reach the default
 * threshold, so news that merely mentions the state (a governor opening a school, a government
 * mortgage rule, a company president) is not treated as politics.
 */
public final class PoliticsRules {

    public enum Group {
        DOMESTIC("domestic"),
        GEO("geo"),
        MILITARY("military");

        public final String key;

        Group(String key) {
            this.key = key;
        }

        public static Group fromKey(String key) {
            for (Group g : values()) {
                if (g.key.equals(key)) return g;
            }
            return null;
        }
    }

    public enum Sensitivity {
        SOFT("soft", 7),
        NORMAL("normal", 4),
        STRICT("strict", 2);

        public final String key;
        public final int threshold;

        Sensitivity(String key, int threshold) {
            this.key = key;
            this.threshold = threshold;
        }

        public static Sensitivity fromKey(String key) {
            for (Sensitivity s : values()) {
                if (s.key.equals(key)) return s;
            }
            return NORMAL;
        }
    }

    /** Phrases that look political to the keyword rules but are not (removed before matching). */
    public static final List<String> DEFAULT_EXCEPTIONS = Collections.unmodifiableList(Arrays.asList(
            "звездные войны", "звездных войн", "войны клонов", "война миров", "война и мир",
            "войну и мир", "войны и мира", "президент компании", "президент клуба",
            "президент федерации", "президента федерации", "президент сбербанка", "президент ржд",
            "партия товара", "партию товара", "партии товара", "выборы лучшего", "выбор редакции",
            "гонка вооружений в играх"));

    private static final Map<Group, List<Term>> DEFAULT_TERMS = buildDefaults();

    private static Map<Group, List<Term>> buildDefaults() {
        Map<Group, List<Term>> m = new EnumMap<>(Group.class);
        List<Term> domestic = new ArrayList<>();
        domestic.addAll(Term.list(3, "путин", "госдум", "депутат", "законопроект", "выборы$", "выборах$",
                "выборов$", "выборам$", "предвыборн", "избирател", "избирком", "цик$", "кремл", "песков",
                "володин", "мишустин", "матвиенко", "единая россия", "единой россии", "лдпр", "кпрф",
                "иноагент", "иностранным агентом", "иностранного агента", "иностранные агенты"));
        domestic.addAll(Term.list(2, "совфед", "совет федерации", "сенатор", "парламент", "оппозиц",
                "митинг", "полпред", "администрация президента", "администрации президента", "политик",
                "политическ", "партии$", "партия$", "партий$", "фракци", "указ президента",
                "указом президента", "референдум", "госсовет", "протестн"));
        domestic.addAll(Term.list(1, "президент", "правительств", "министр", "губернатор", "мэр$", "мэра$",
                "мэрии$", "закон$", "закона$", "законы$", "власт", "чиновник", "протест", "голосовани"));
        m.put(Group.DOMESTIC, Collections.unmodifiableList(domestic));

        List<Term> geo = new ArrayList<>();
        geo.addAll(Term.list(3, "санкци", "нато$", "трамп", "зеленск", "лавров", "захарова$", "мид$",
                "мида$", "госдеп", "байден", "макрон", "эрдоган", "си цзиньпин", "лукашенк", "нетаньяху",
                "орбан", "шольц", "мерц$"));
        geo.addAll(Term.list(2, "дипломат", "посол", "посольств", "саммит", "переговор", "генсек", "оон$",
                "белый дом", "белого дома", "евросоюз", "геополит", "пентагон", "вашингтон", "брюссел"));
        geo.addAll(Term.list(1, "киев", "пекин", "ес$", "сша$", "украин", "израил", "иран", "европейск"));
        m.put(Group.GEO, Collections.unmodifiableList(geo));

        List<Term> military = new ArrayList<>();
        military.addAll(Term.list(3, "сво$", "специальной военной операци", "всу$", "минобороны",
                "министерство обороны", "министерства обороны", "мобилизац", "обстрел", "ракетный удар",
                "ракетного удара", "военнослужащ"));
        military.addAll(Term.list(2, "армия$", "армии$", "фронт$", "фронте$", "фронта$", "бпла",
                "беспилотник", "боевик", "теракт", "наступлени", "контрнаступ", "война$", "войны$",
                "войне$", "войну$", "боевые действия", "боевых действий", "пво$", "сбит", "войска"));
        military.addAll(Term.list(1, "военн", "оборон", "атака$", "атаки$", "удар по"));
        m.put(Group.MILITARY, Collections.unmodifiableList(military));
        return m;
    }

    private final Set<Group> enabledGroups;
    private final Sensitivity sensitivity;
    private final List<Term> blockTerms;
    private final List<Term> allowTerms;

    public PoliticsRules(Set<Group> enabledGroups, Sensitivity sensitivity, String userBlockWords,
                         String userAllowWords) {
        this.enabledGroups = enabledGroups.isEmpty() ? EnumSet.noneOf(Group.class) : EnumSet.copyOf(enabledGroups);
        this.sensitivity = sensitivity;
        this.blockTerms = Term.parseUserList(userBlockWords, 3);
        this.allowTerms = Term.parseUserList(userAllowWords, 0);
    }

    public static PoliticsRules defaults() {
        return new PoliticsRules(EnumSet.allOf(Group.class), Sensitivity.NORMAL, "", "");
    }

    public static List<Term> defaultTerms(Group group) {
        return DEFAULT_TERMS.get(group);
    }

    public Sensitivity sensitivity() {
        return sensitivity;
    }

    public Set<Group> enabledGroups() {
        return Collections.unmodifiableSet(enabledGroups);
    }

    public PoliticsResult evaluate(String title, String body) {
        List<String> titleTokens = tokensWithoutExceptions(title);
        List<String> bodyTokens = tokensWithoutExceptions(body);

        for (Term allow : allowTerms) {
            if (allow.matches(titleTokens) || allow.matches(bodyTokens)) {
                return new PoliticsResult(0, false, Collections.emptyList(), allow.display);
            }
        }

        int score = 0;
        List<String> matches = new ArrayList<>();
        for (Group g : Group.values()) {
            if (!enabledGroups.contains(g)) continue;
            score += scoreTerms(DEFAULT_TERMS.get(g), titleTokens, bodyTokens, matches);
        }
        score += scoreTerms(blockTerms, titleTokens, bodyTokens, matches);
        boolean political = score >= sensitivity.threshold;
        return new PoliticsResult(score, political, matches, null);
    }

    private static int scoreTerms(List<Term> terms, List<String> titleTokens, List<String> bodyTokens,
                                  List<String> matches) {
        int score = 0;
        for (Term t : terms) {
            if (t.matches(titleTokens)) {
                score += 2 * t.weight;
                if (!matches.contains(t.display)) matches.add(t.display);
            } else if (t.matches(bodyTokens)) {
                score += t.weight;
                if (!matches.contains(t.display)) matches.add(t.display);
            }
        }
        return score;
    }

    static List<String> tokensWithoutExceptions(String text) {
        String norm = TextNormalizer.normalize(text);
        if (norm.isEmpty()) return Collections.emptyList();
        String padded = " " + norm + " ";
        for (String exc : DEFAULT_EXCEPTIONS) {
            padded = padded.replace(" " + exc + " ", " ");
        }
        String cleaned = padded.trim();
        if (cleaned.isEmpty()) return Collections.emptyList();
        return Arrays.asList(cleaned.split(" +"));
    }
}
