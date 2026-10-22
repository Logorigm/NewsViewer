package com.newshub.app.ui.search;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavOptions;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.newshub.app.AppContainer;
import com.newshub.app.NewsHubApp;
import com.newshub.app.R;
import com.newshub.app.data.db.model.FeedRow;
import com.newshub.app.domain.Category;
import com.newshub.app.ui.article.ArticleFragment;
import com.newshub.app.ui.common.CategoryUi;
import com.newshub.app.ui.common.Network;
import com.newshub.app.ui.common.StateView;
import com.newshub.app.ui.feed.FeedAdapter;
import com.newshub.app.ui.feed.FeedItem;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SearchFragment extends Fragment implements FeedAdapter.Listener {

    private static final long DEBOUNCE_MILLIS = 300;

    private AppContainer c;
    private SearchViewModel vm;
    private FeedAdapter adapter;
    private StateView state;
    private EditText input;
    private View clear;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Map<Integer, Category> chipCategories = new HashMap<>();
    private Category selectedCategory;
    private final Runnable runSearch = () -> vm.search(input.getText().toString(), selectedCategory);

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_search, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        c = NewsHubApp.container(requireContext());
        vm = new ViewModelProvider(this).get(SearchViewModel.class);
        selectedCategory = vm.category();
        state = new StateView(view.findViewById(R.id.state));
        input = view.findViewById(R.id.query);
        clear = view.findViewById(R.id.clear);
        view.findViewById(R.id.back).setOnClickListener(v -> {
            hideKeyboard();
            NavHostFragment.findNavController(this).navigateUp();
        });
        clear.setOnClickListener(v -> input.setText(""));

        RecyclerView list = view.findViewById(R.id.list);
        adapter = new FeedAdapter(this);
        list.setLayoutManager(new GridLayoutManager(requireContext(), getResources().getInteger(R.integer.feed_columns)));
        list.setAdapter(adapter);
        list.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView rv, int newState) {
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING) hideKeyboard();
            }
        });

        buildChips(view.findViewById(R.id.chips));

        input.setText(vm.query());
        input.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                clear.setVisibility(s.length() > 0 ? View.VISIBLE : View.GONE);
                handler.removeCallbacks(runSearch);
                handler.postDelayed(runSearch, DEBOUNCE_MILLIS);
            }
        });
        input.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                handler.removeCallbacks(runSearch);
                runSearch.run();
                hideKeyboard();
                return true;
            }
            return false;
        });

        vm.results().observe(getViewLifecycleOwner(), this::render);
        render(vm.results().getValue());

        if (vm.query().isEmpty()) {
            input.requestFocus();
            input.post(() -> {
                InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
            });
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        adapter.setDisplayOptions(c.settings.textScale(),
                Network.shouldLoadImages(requireContext(), c.settings.imagePolicy()));
    }

    @Override
    public void onDestroyView() {
        handler.removeCallbacks(runSearch);
        super.onDestroyView();
    }

    private void buildChips(ChipGroup chips) {
        Chip all = addChip(chips, getString(R.string.search_all_categories), null);
        Chip toCheck = all;
        for (Category cat : Category.values()) {
            Chip chip = addChip(chips, getString(CategoryUi.title(cat)), cat);
            if (cat == selectedCategory) toCheck = chip;
        }
        chips.check(toCheck.getId());
        chips.setOnCheckedStateChangeListener((group, ids) -> {
            if (ids.isEmpty()) return;
            selectedCategory = chipCategories.get(ids.get(0));
            handler.removeCallbacks(runSearch);
            runSearch.run();
        });
    }

    private Chip addChip(ChipGroup group, String text, Category category) {
        Chip chip = (Chip) getLayoutInflater().inflate(R.layout.item_chip_filter, group, false);
        chip.setId(View.generateViewId());
        chip.setText(text);
        group.addView(chip);
        chipCategories.put(chip.getId(), category);
        return chip;
    }

    private void render(List<FeedRow> rows) {
        if (rows == null) {
            adapter.submitList(new ArrayList<>());
            state.show(R.drawable.ic_search, R.string.search_hint_title, getString(R.string.search_hint_message), 0, null);
            return;
        }
        List<FeedItem> items = new ArrayList<>(rows.size());
        boolean grouped = vm.grouped();
        for (FeedRow r : rows) items.add(new FeedItem(r, null, grouped));
        adapter.submitList(items);
        if (items.isEmpty()) {
            state.show(R.drawable.ic_search, R.string.search_empty_title,
                    getString(R.string.search_empty_message, vm.query()), 0, null);
        } else {
            state.hide();
        }
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null && input != null) imm.hideSoftInputFromWindow(input.getWindowToken(), 0);
    }

    @Override
    public void onOpen(FeedItem item) {
        hideKeyboard();
        NavHostFragment.findNavController(this).navigate(R.id.articleFragment, ArticleFragment.args(item.row.articleId),
                new NavOptions.Builder().setEnterAnim(R.anim.slide_in_right).setExitAnim(R.anim.slide_out_left)
                        .setPopEnterAnim(R.anim.slide_in_left).setPopExitAnim(R.anim.slide_out_right).build());
    }

    @Override
    public void onToggleFavorite(FeedItem item) {
        vm.toggleFavorite(item.row.articleId);
    }

    @Override
    public void onWhy(FeedItem item) {
        // no ranking reasons in search
    }
}
