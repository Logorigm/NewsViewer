package com.newshub.app.ui.library;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavOptions;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.tabs.TabLayout;
import com.newshub.app.AppContainer;
import com.newshub.app.NewsHubApp;
import com.newshub.app.R;
import com.newshub.app.data.db.model.FeedRow;
import com.newshub.app.ui.article.ArticleFragment;
import com.newshub.app.ui.common.Network;
import com.newshub.app.ui.common.StateView;
import com.newshub.app.ui.feed.FeedAdapter;
import com.newshub.app.ui.feed.FeedItem;

import java.util.ArrayList;
import java.util.List;

/** Favorites (kept offline, never removed by cache cleanup) and reading history. */
public class LibraryFragment extends Fragment implements FeedAdapter.Listener {

    private static final int TAB_FAVORITES = 0;
    private static final int TAB_HISTORY = 1;

    private AppContainer c;
    private LibraryViewModel vm;
    private FeedAdapter adapter;
    private StateView state;
    private MaterialToolbar toolbar;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_library, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        c = NewsHubApp.container(requireContext());
        vm = new ViewModelProvider(this).get(LibraryViewModel.class);
        toolbar = view.findViewById(R.id.toolbar);
        state = new StateView(view.findViewById(R.id.state));
        RecyclerView list = view.findViewById(R.id.list);
        adapter = new FeedAdapter(this);
        list.setLayoutManager(new GridLayoutManager(requireContext(), getResources().getInteger(R.integer.feed_columns)));
        list.setAdapter(adapter);

        TabLayout tabs = view.findViewById(R.id.tabs);
        tabs.addTab(tabs.newTab().setText(R.string.tab_favorites), vm.tab == TAB_FAVORITES);
        tabs.addTab(tabs.newTab().setText(R.string.tab_history), vm.tab == TAB_HISTORY);
        tabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                vm.tab = tab.getPosition();
                render();
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
                list.smoothScrollToPosition(0);
            }
        });

        toolbar.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.action_clear_history) {
                new MaterialAlertDialogBuilder(requireContext())
                        .setTitle(R.string.history_clear)
                        .setMessage(R.string.history_clear_confirm)
                        .setPositiveButton(R.string.action_clear, (d, w) -> vm.clearHistory())
                        .setNegativeButton(R.string.action_cancel, null)
                        .show();
                return true;
            }
            if (id == R.id.action_search) {
                NavHostFragment.findNavController(this).navigate(R.id.searchFragment, null, new NavOptions.Builder()
                        .setEnterAnim(R.anim.slide_in_right).setExitAnim(R.anim.slide_out_left)
                        .setPopEnterAnim(R.anim.slide_in_left).setPopExitAnim(R.anim.slide_out_right).build());
                return true;
            }
            return false;
        });

        vm.favorites.observe(getViewLifecycleOwner(), rows -> render());
        vm.history.observe(getViewLifecycleOwner(), rows -> render());
    }

    @Override
    public void onResume() {
        super.onResume();
        adapter.setDisplayOptions(c.settings.textScale(),
                Network.shouldLoadImages(requireContext(), c.settings.imagePolicy()));
    }

    private void render() {
        boolean history = vm.tab == TAB_HISTORY;
        toolbar.getMenu().findItem(R.id.action_clear_history).setVisible(history);
        List<FeedRow> rows = history ? vm.history.getValue() : vm.favorites.getValue();
        List<FeedItem> items = new ArrayList<>();
        if (rows != null) {
            for (FeedRow r : rows) items.add(new FeedItem(r, null, false));
        }
        adapter.submitList(items);
        if (rows == null || !rows.isEmpty()) {
            state.hide();
        } else if (history) {
            state.show(R.drawable.ic_history, R.string.history_empty_title, getString(R.string.history_empty_message), 0, null);
        } else {
            state.show(R.drawable.ic_bookmark_border, R.string.favorites_empty_title,
                    getString(R.string.favorites_empty_message), 0, null);
        }
    }

    @Override
    public void onOpen(FeedItem item) {
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
        // reasons are not shown in the library
    }
}
