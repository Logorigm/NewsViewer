package com.newshub.app.domain.feed;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/** Applies the "ограничивать" politics mode to an already ordered list. */
public final class PoliticsLimiter {

    private PoliticsLimiter() {
    }

    public static final double DEFAULT_SHARE = 0.2;

    /**
     * Keeps the order and drops political items whenever keeping them would make political
     * items exceed {@code share} of the list so far (the first political item is allowed only
     * after a few non-political ones).
     */
    public static <T> List<T> cap(List<T> items, Predicate<T> isPolitical, double share) {
        List<T> out = new ArrayList<>(items.size());
        int political = 0;
        for (T item : items) {
            if (isPolitical.test(item)) {
                int wouldBe = political + 1;
                if (wouldBe > share * (out.size() + 1)) continue;
                political = wouldBe;
            }
            out.add(item);
        }
        return out;
    }
}
