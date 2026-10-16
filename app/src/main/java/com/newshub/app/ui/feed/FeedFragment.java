package com.newshub.app.ui.feed;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavOptions;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.snackbar.Snackbar;
import com.newshub.app.AppContainer;
import com.newshub.app.NewsHubApp;
import com.newshub.app.R;
import com.newshub.app.data.db.entity.SourceEntity;
import com.newshub.app.data.repo.RefreshState;
import com.newshub.app.data.repo.RefreshSummary;
import com.newshub.app.domain.Category;
import com.newshub.app.domain.feed.FeedFilter;
import com.newshub.app.domain.feed.FeedMode;
import com.newshub.app.domain.feed.Period;
import com.newshub.app.domain.feed.SortOrder;
import com.newshub.app.ui.MainActivity;
import com.newshub.app.ui.article.ArticleFragment;
import com.newshub.app.ui.common.CategoryUi;
import com.newshub.app.ui.common.Dimens;
import com.newshub.app.ui.common.Network;
import com.newshub.app.ui.common.Skeleton;
import com.newshub.app.ui.common.StateView;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Feed screen. Used for the main feed, a single category ("screen" = "category") and the list of
 * publications hidden by the politics filter ("screen" = "hidden").
 */
public class FeedFragment extends Fragment implements FeedAdapter.Listener {

    public static final String ARG_SCREEN = "screen";
    public static final String ARG_CATEGORY = "category";
    public static final String SCREEN_MAIN = "main";
    public static final String SCREEN_CATEGORY = "category";
    public static final String SCREEN_HIDDEN = "hidden";

    private AppContainer c;
    private FeedViewModel vm;
    private FeedAdapter adapter;
    private String screen;
    private Category screenCategory;

    private MaterialToolbar toolbar;
    private ChipGroup chips;
    private TextView filterSummary;
    private TextView banner;
    private LinearProgressIndicator progress;
    private SwipeRefreshLayout swipe;
    private RecyclerView list;
    private LinearLayout skeleton;
    private StateView state;

    private final Map<Integer, Object> chipTargets = new HashMap<>();
    private final Map<String, String> sourceNames = new HashMap<>();
    private int enabledSources = -1;
    private boolean wasRefreshing;
    private RefreshSummary lastSummary;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_feed, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        c = NewsHubApp.container(requireContext());
        Bundle args = getArguments();
        screen = args != null ? args.getString(ARG_SCREEN, SCREEN_MAIN) : SCREEN_MAIN;
        screenCategory = Category.fromKey(args != null ? args.getString(ARG_CATEGORY) : null, Category.MAIN);

        toolbar = view.findViewById(R.id.toolbar);
        chips = view.findViewById(R.id.mode_chips);
        HorizontalScrollView chipsScroll = view.findViewById(R.id.chips_scroll);
        filterSummary = view.findViewById(R.id.filter_summary);
        banner = view.findViewById(R.id.banner);
        progress = view.findViewById(R.id.progress);
        swipe = view.findViewById(R.id.swipe);
        list = view.findViewById(R.id.list);
        skeleton = view.findViewById(R.id.skeleton);
        state = new StateView(view.findViewById(R.id.state));

        vm = new ViewModelProvider(this).get(FeedViewModel.class);
        FeedMode initialMode;
        Category initialCategory = null;
        if (SCREEN_CATEGORY.equals(screen)) {
            initialMode = FeedMode.CATEGORY;
            initialCategory = screenCategory;
        } else if (SCREEN_HIDDEN.equals(screen)) {
            initialMode = FeedMode.POLITICAL_ONLY;
        } else {
            initialMode = c.settings.defaultMode();
        }
        vm.init(initialMode, initialCategory);

        setupToolbar();
        if (SCREEN_MAIN.equals(screen)) {
            buildChips();
        } else {
            chipsScroll.setVisibility(View.GONE);
        }

        adapter = new FeedAdapter(this);
        adapter.setShowPoliticalReasons(SCREEN_HIDDEN.equals(screen));
        list.setLayoutManager(new GridLayoutManager(requireContext(), getResources().getInteger(R.integer.feed_columns)));
        list.setAdapter(adapter);

        swipe.setColorSchemeColors(ContextCompat.getColor(requireContext(), R.color.nh_accent));
        swipe.setProgressBackgroundColorSchemeColor(ContextCompat.getColor(requireContext(), R.color.nh_surface_high));
        swipe.setOnChildScrollUpCallback((parent, child) -> list.canScrollVertically(-1));
        swipe.setOnRefreshListener(vm::refresh);

        view.findViewById(R.id.filter_row).setOnClickListener(v ->
                new FeedFilterSheet().show(getChildFragmentManager(), "filters"));

        vm.items().observe(getViewLifecycleOwner(), items -> render());
        vm.hiddenPolitical().observe(getViewLifecycleOwner(), n -> updateBanner());
        vm.filter().observe(getViewLifecycleOwner(), this::onFilterChanged);
        c.news.refreshState().observe(getViewLifecycleOwner(), this::onRefreshState);
        c.sources.observeSources().observe(getViewLifecycleOwner(), sources -> {
            int enabled = 0;
            sourceNames.clear();
            for (SourceEntity s : sources) {
                sourceNames.put(s.id, s.name);
                if (s.enabled && !s.address.isEmpty()) enabled++;
            }
            enabledSources = enabled;
            onFilterChanged(vm.currentFilter());
            render();
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        vm.applySettings();
        adapter.setDisplayOptions(c.settings.textScale(),
                Network.shouldLoadImages(requireContext(), c.settings.imagePolicy()));
        if (SCREEN_MAIN.equals(screen)) vm.refreshIfStale();
    }

    private void setupToolbar() {
        if (SCREEN_MAIN.equals(screen)) {
            toolbar.setTitle(R.string.app_name);
        } else {
            toolbar.setTitle(SCREEN_HIDDEN.equals(screen) ? getString(R.string.hidden_title)
                    : getString(CategoryUi.title(screenCategory)));
            toolbar.setNavigationIcon(R.drawable.ic_arrow_back);
            toolbar.setNavigationContentDescription(R.string.action_back);
            toolbar.setNavigationOnClickListener(v -> NavHostFragment.findNavController(this).navigateUp());
        }
        toolbar.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.action_search) {
                NavHostFragment.findNavController(this).navigate(R.id.searchFragment, null, slideOptions());
                return true;
            }
            if (id == R.id.action_refresh) {
                vm.refresh();
                return true;
            }
            return false;
        });
    }

    private void buildChips() {
        chips.removeAllViews();
        chipTargets.clear();
        addChip(R.string.mode_all, FeedMode.ALL);
        addChip(R.string.mode_no_politics, FeedMode.NO_POLITICS);
        addChip(R.string.mode_interesting, FeedMode.INTERESTING);
        for (Category cat : Category.values()) {
            addChip(CategoryUi.title(cat), cat);
        }
        chips.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) return;
            Object target = chipTargets.get(checkedIds.get(0));
            if (target instanceof FeedMode) {
                vm.setMode((FeedMode) target, null);
            } else if (target instanceof Category) {
                vm.setMode(FeedMode.CATEGORY, (Category) target);
            }
            list.scrollToPosition(0);
        });
    }

    private void addChip(int titleRes, Object target) {
        Chip chip = (Chip) getLayoutInflater().inflate(R.layout.item_chip_filter, chips, false);
        chip.setId(View.generateViewId());
        chip.setText(titleRes);
        chips.addView(chip);
        chipTargets.put(chip.getId(), target);
    }

    private void onFilterChanged(FeedFilter f) {
        if (f == null) return;
        if (SCREEN_MAIN.equals(screen)) {
            for (Map.Entry<Integer, Object> e : chipTargets.entrySet()) {
                Object t = e.getValue();
                boolean match = (t instanceof FeedMode && f.mode == t)
                        || (t instanceof Category && f.mode == FeedMode.CATEGORY && f.category == t);
                if (match && chips.getCheckedChipId() != e.getKey()) chips.check(e.getKey());
            }
        }
        StringBuilder sb = new StringBuilder(getString(periodLabel(f.period)));
        sb.append(" · ").append(getString(f.sort == SortOrder.MOST_SOURCES ? R.string.sort_sources : R.string.sort_newest));
        if (f.sourceId != null) {
            String name = sourceNames.get(f.sourceId);
            sb.append(" · ").append(name != null ? name : f.sourceId);
        }
        if (!f.groupDuplicates) sb.append(" · ").append(getString(R.string.filter_ungrouped));
        filterSummary.setText(sb);
        updateBanner();
    }

    private static int periodLabel(Period p) {
        switch (p) {
            case DAY:
                return R.string.period_day;
            case MONTH:
                return R.string.period_month;
            case YEAR:
                return R.string.period_year;
            case ALL:
                return R.string.period_all;
            case WEEK:
            default:
                return R.string.period_week;
        }
    }

    private void onRefreshState(RefreshState s) {
        if (s == null) return;
        progress.setVisibility(s.running ? View.VISIBLE : View.GONE);
        if (!s.running) swipe.setRefreshing(false);
        if (s.last != null) lastSummary = s.last;
        if (wasRefreshing && !s.running) {
            vm.reload();
            if (lastSummary != null && lastSummary.newArticles > 0 && list.canScrollVertically(-1)) {
                Snackbar.make(list, getResources().getQuantityString(R.plurals.new_items_snackbar,
                                lastSummary.newArticles, lastSummary.newArticles), Snackbar.LENGTH_LONG)
                        .setAction(R.string.action_scroll_top, v -> list.smoothScrollToPosition(0))
                        .show();
            }
        }
        wasRefreshing = s.running;
        updateBanner();
        render();
    }

    private void render() {
        List<FeedItem> items = vm.items().getValue();
        boolean refreshing = c.news.isRefreshing();
        if (items == null) {
            Skeleton.show(skeleton);
            state.hide();
            return;
        }
        adapter.submitList(items);
        if (!items.isEmpty()) {
            Skeleton.hide(skeleton);
            state.hide();
            return;
        }
        if (refreshing && c.settings.lastRefreshAt() == 0) {
            Skeleton.show(skeleton);
            state.hide();
            return;
        }
        Skeleton.hide(skeleton);
        if (enabledSources == 0) {
            state.show(R.drawable.ic_sources, R.string.empty_no_sources_title,
                    getString(R.string.empty_no_sources_message), R.string.empty_no_sources_action,
                    v -> openSourcesTab());
        } else if (lastSummary != null && lastSummary.looksOffline()) {
            state.show(R.drawable.ic_cloud_off, R.string.error_offline_title,
                    getString(R.string.error_offline_message), R.string.action_retry, v -> vm.refresh());
        } else if (SCREEN_HIDDEN.equals(screen)) {
            state.show(R.drawable.ic_visibility_off, R.string.hidden_empty_title,
                    getString(R.string.hidden_empty_message), 0, null);
        } else if (c.settings.lastRefreshAt() == 0 && !refreshing) {
            state.show(R.drawable.ic_refresh, R.string.empty_first_title,
                    getString(R.string.empty_first_message), R.string.action_refresh, v -> vm.refresh());
        } else if (vm.currentFilter().period != Period.ALL) {
            state.show(R.drawable.ic_feed, R.string.empty_period_title,
                    getString(R.string.empty_period_message), R.string.empty_period_action,
                    v -> vm.applyFilter(Period.ALL, vm.currentFilter().sort, vm.currentFilter().sourceId));
        } else {
            state.show(R.drawable.ic_feed, R.string.empty_nothing_title,
                    getString(R.string.empty_nothing_message), 0, null);
        }
    }

    private void updateBanner() {
        if (banner == null) return;
        Integer hidden = vm.hiddenPolitical().getValue();
        FeedFilter f = vm.currentFilter();
        if (lastSummary != null && lastSummary.looksOffline() && !Network.isOnline(requireContext())) {
            showBanner(getString(R.string.banner_offline), R.drawable.ic_cloud_off, v -> vm.refresh());
        } else if (hidden != null && hidden > 0 && (f.hidesPolitics() || f.limitsPolitics())) {
            String text = getResources().getQuantityString(R.plurals.banner_hidden_politics, hidden, hidden);
            showBanner(text, R.drawable.ic_visibility_off, v -> NavHostFragment.findNavController(this)
                    .navigate(R.id.hiddenFragment, null, slideOptions()));
        } else if (SCREEN_MAIN.equals(screen) && lastSummary != null && lastSummary.sourcesFailed > 0
                && !lastSummary.looksOffline()) {
            String text = getResources().getQuantityString(R.plurals.banner_failed_sources,
                    lastSummary.sourcesFailed, lastSummary.sourcesFailed);
            showBanner(text, R.drawable.ic_warning, v -> openSourcesTab());
        } else if (SCREEN_HIDDEN.equals(screen)) {
            showBanner(getString(R.string.hidden_explanation), R.drawable.ic_info, null);
        } else {
            banner.setVisibility(View.GONE);
        }
    }

    private void showBanner(String text, int icon, View.OnClickListener click) {
        banner.setText(text);
        banner.setCompoundDrawablesRelative(Dimens.smallIcon(requireContext(), icon, 20, R.color.nh_text_secondary),
                null, null, null);
        banner.setOnClickListener(click);
        banner.setClickable(click != null);
        banner.setVisibility(View.VISIBLE);
    }

    private void openSourcesTab() {
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).selectTab(R.id.sourcesFragment);
        }
    }

    static NavOptions slideOptions() {
        return new NavOptions.Builder()
                .setEnterAnim(R.anim.slide_in_right)
                .setExitAnim(R.anim.slide_out_left)
                .setPopEnterAnim(R.anim.slide_in_left)
                .setPopExitAnim(R.anim.slide_out_right)
                .build();
    }

    // ---- card callbacks ------------------------------------------------------------------------

    @Override
    public void onOpen(FeedItem item) {
        NavHostFragment.findNavController(this).navigate(R.id.articleFragment,
                ArticleFragment.args(item.row.articleId), slideOptions());
    }

    @Override
    public void onToggleFavorite(FeedItem item) {
        vm.toggleFavorite(item);
    }

    @Override
    public void onWhy(FeedItem item) {
        Category category = Category.fromKey(item.row.primaryCategory, Category.WORLD);
        String topic = getString(CategoryUi.title(category));
        if (item.reasons != null) {
            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.why_title)
                    .setMessage(ReasonFormatter.format(requireContext(), item.reasons))
                    .setPositiveButton(getString(R.string.why_more_like, topic), (d, w) -> vm.setInterest(category, 1))
                    .setNegativeButton(getString(R.string.why_less_like, topic), (d, w) -> vm.setInterest(category, -1))
                    .setNeutralButton(R.string.action_close, null)
                    .show();
        } else {
            String matches = item.row.politicsMatches == null ? "—" : item.row.politicsMatches;
            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.why_hidden_title)
                    .setMessage(getString(R.string.why_hidden_message, matches))
                    .setPositiveButton(R.string.politics_mark_not, (d, w) ->
                            c.news.setPoliticsOverride(item.row.clusterId, -1))
                    .setNeutralButton(R.string.action_close, null)
                    .show();
        }
    }
}
