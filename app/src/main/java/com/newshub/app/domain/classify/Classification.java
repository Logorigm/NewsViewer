package com.newshub.app.domain.classify;

import com.newshub.app.domain.Category;

import java.util.Collections;
import java.util.List;

/** Topic and politics verdict for one publication. */
public final class Classification {
    public final Category primary;
    public final List<Category> categories;
    public final int categoryMask;
    public final PoliticsResult politics;

    public Classification(Category primary, List<Category> categories, PoliticsResult politics) {
        this.primary = primary;
        this.categories = Collections.unmodifiableList(categories);
        this.categoryMask = Category.maskOf(categories);
        this.politics = politics;
    }
}
