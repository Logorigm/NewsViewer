package com.newshub.app.ui.categories;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.LiveData;
import androidx.navigation.NavOptions;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.newshub.app.AppContainer;
import com.newshub.app.NewsHubApp;
import com.newshub.app.R;
import com.newshub.app.domain.Category;
import com.newshub.app.ui.common.CategoryUi;
import com.newshub.app.ui.feed.FeedFragment;
import com.newshub.app.ui.settings.InterestsDialog;

import java.util.List;

public class CategoriesFragment extends Fragment implements CategoryAdapter.Listener {

    private AppContainer c;
    private CategoryAdapter adapter;
    private LiveData<List<Integer>> masks;
    private List<Integer> lastMasks;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_categories, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        c = NewsHubApp.container(requireContext());
        MaterialToolbar toolbar = view.findViewById(R.id.toolbar);
        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_interests) {
                InterestsDialog.show(requireContext(), c.settings, this::updateCounts);
                return true;
            }
            return false;
        });
        RecyclerView list = view.findViewById(R.id.list);
        list.setLayoutManager(new GridLayoutManager(requireContext(), getResources().getInteger(R.integer.category_columns)));
        adapter = new CategoryAdapter(this);
        list.setAdapter(adapter);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (masks != null) masks.removeObservers(getViewLifecycleOwner());
        masks = c.news.observeCategoryMasks(System.currentTimeMillis() - 86_400_000L);
        masks.observe(getViewLifecycleOwner(), m -> {
            lastMasks = m;
            updateCounts();
        });
    }

    private void updateCounts() {
        int[] counts = new int[Category.values().length];
        if (lastMasks != null) {
            for (Integer mask : lastMasks) {
                if (mask == null) continue;
                for (Category cat : Category.values()) {
                    if ((mask & cat.mask()) != 0) counts[cat.ordinal()]++;
                }
            }
        }
        int[] interests = new int[Category.values().length];
        for (Category cat : Category.values()) interests[cat.ordinal()] = c.settings.interest(cat);
        adapter.setCounts(counts, interests);
    }

    @Override
    public void onOpen(Category category) {
        Bundle args = new Bundle();
        args.putString(FeedFragment.ARG_SCREEN, FeedFragment.SCREEN_CATEGORY);
        args.putString(FeedFragment.ARG_CATEGORY, category.key);
        NavOptions options = new NavOptions.Builder()
                .setEnterAnim(R.anim.slide_in_right)
                .setExitAnim(R.anim.slide_out_left)
                .setPopEnterAnim(R.anim.slide_in_left)
                .setPopExitAnim(R.anim.slide_out_right)
                .build();
        NavHostFragment.findNavController(this).navigate(R.id.categoryFeedFragment, args, options);
    }

    @Override
    public void onLongPress(Category category) {
        if (category == Category.MAIN) return;
        String[] options = {getString(R.string.interest_more_long), getString(R.string.interest_neutral_long),
                getString(R.string.interest_less_long)};
        int current = c.settings.interest(category);
        int checked = current > 0 ? 0 : current < 0 ? 2 : 1;
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(CategoryUi.title(category))
                .setSingleChoiceItems(options, checked, (d, which) -> {
                    c.settings.setInterest(category, which == 0 ? 1 : which == 2 ? -1 : 0);
                    updateCounts();
                    d.dismiss();
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }
}
