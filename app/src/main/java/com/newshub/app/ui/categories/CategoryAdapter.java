package com.newshub.app.ui.categories;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.newshub.app.R;
import com.newshub.app.domain.Category;
import com.newshub.app.ui.common.CategoryUi;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Category tiles with the number of stories in the last 24 hours. */
public class CategoryAdapter extends RecyclerView.Adapter<CategoryAdapter.Holder> {

    public interface Listener {
        void onOpen(Category category);

        void onLongPress(Category category);
    }

    private final List<Category> categories = new ArrayList<>(Arrays.asList(Category.values()));
    private final Listener listener;
    private int[] counts = new int[Category.values().length];
    private int[] interests = new int[Category.values().length];

    public CategoryAdapter(Listener listener) {
        this.listener = listener;
    }

    public void setCounts(int[] counts, int[] interests) {
        this.counts = counts;
        this.interests = interests;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_category, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        Category c = categories.get(position);
        Context ctx = h.itemView.getContext();
        String name = ctx.getString(CategoryUi.title(c));
        h.name.setText(name);
        h.accent.setBackgroundColor(ContextCompat.getColor(ctx, CategoryUi.color(c)));
        int n = counts[c.ordinal()];
        h.count.setText(n == 0 ? ctx.getString(R.string.category_count_zero)
                : ctx.getResources().getQuantityString(R.plurals.category_count, n, n));
        int interest = interests[c.ordinal()];
        if (interest != 0 && c != Category.MAIN) {
            h.interest.setVisibility(View.VISIBLE);
            h.interest.setText(interest > 0 ? R.string.interest_badge_more : R.string.interest_badge_less);
        } else {
            h.interest.setVisibility(View.GONE);
        }
        h.card.setContentDescription(name + ", " + h.count.getText());
        h.card.setOnClickListener(v -> listener.onOpen(c));
        h.card.setOnLongClickListener(v -> {
            listener.onLongPress(c);
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return categories.size();
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final View card;
        final View accent;
        final TextView name;
        final TextView count;
        final TextView interest;

        Holder(View v) {
            super(v);
            card = v.findViewById(R.id.card);
            accent = v.findViewById(R.id.accent);
            name = v.findViewById(R.id.name);
            count = v.findViewById(R.id.count);
            interest = v.findViewById(R.id.interest);
        }
    }
}
