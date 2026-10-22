package com.newshub.app.ui.article;

import android.os.Bundle;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.newshub.app.AppContainer;
import com.newshub.app.NewsHubApp;
import com.newshub.app.R;
import com.newshub.app.data.db.entity.ArticleEntity;
import com.newshub.app.data.db.entity.ClusterEntity;
import com.newshub.app.domain.Category;
import com.newshub.app.domain.text.TimeFormat;
import com.newshub.app.ui.common.CategoryUi;
import com.newshub.app.ui.common.Images;
import com.newshub.app.ui.common.Links;
import com.newshub.app.ui.common.Network;

import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A publication: headline, image, short excerpt, source and time, the button to the original,
 * every source that reported the same story, and why it is (or is not) marked as politics.
 */
public class ArticleFragment extends Fragment {

    public static final String ARG_ID = "articleId";

    private AppContainer c;
    private ArticleViewModel vm;
    private ArticleEntity current;
    private ClusterEntity currentCluster;
    private long observedClusterId = -1;

    private MaterialToolbar toolbar;
    private TextView category;
    private TextView title;
    private TextView meta;
    private TextView badge;
    private ImageView image;
    private TextView summary;
    private MaterialButton openOriginal;
    private View politicsBox;
    private TextView politicsText;
    private MaterialButton politicsToggle;
    private TextView clusterHeader;
    private TextView clusterTimes;
    private LinearLayout clusterList;

    public static Bundle args(long articleId) {
        Bundle b = new Bundle();
        b.putLong(ARG_ID, articleId);
        return b;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_article, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        c = NewsHubApp.container(requireContext());
        long id = requireArguments().getLong(ARG_ID, 0);
        vm = new ViewModelProvider(this).get(ArticleViewModel.class);
        vm.init(id);

        toolbar = view.findViewById(R.id.toolbar);
        category = view.findViewById(R.id.category);
        title = view.findViewById(R.id.title);
        meta = view.findViewById(R.id.meta);
        badge = view.findViewById(R.id.badge);
        image = view.findViewById(R.id.image);
        summary = view.findViewById(R.id.summary);
        openOriginal = view.findViewById(R.id.open_original);
        politicsBox = view.findViewById(R.id.politics_box);
        politicsText = view.findViewById(R.id.politics_text);
        politicsToggle = view.findViewById(R.id.politics_toggle);
        clusterHeader = view.findViewById(R.id.cluster_header);
        clusterTimes = view.findViewById(R.id.cluster_times);
        clusterList = view.findViewById(R.id.cluster_list);

        toolbar.setNavigationOnClickListener(v -> NavHostFragment.findNavController(this).navigateUp());
        toolbar.setOnMenuItemClickListener(this::onMenuItem);
        openOriginal.setOnClickListener(v -> {
            if (current != null) Links.open(requireContext(), current.url);
        });

        vm.article().observe(getViewLifecycleOwner(), a -> {
            if (a == null) {
                showMissing();
                return;
            }
            current = a;
            bindArticle(a);
            vm.markReadOnce();
            if (observedClusterId != a.clusterId) {
                observedClusterId = a.clusterId;
                vm.cluster(a.clusterId).observe(getViewLifecycleOwner(), cl -> {
                    currentCluster = cl;
                    bindPolitics();
                });
                vm.clusterArticles(a.clusterId).observe(getViewLifecycleOwner(), this::bindCluster);
            }
        });
        vm.favorite().observe(getViewLifecycleOwner(), fav -> {
            MenuItem item = toolbar.getMenu().findItem(R.id.action_favorite);
            boolean on = fav != null && fav;
            item.setIcon(on ? R.drawable.ic_bookmark : R.drawable.ic_bookmark_border);
            item.setTitle(on ? R.string.action_remove_favorite : R.string.action_add_favorite);
        });
    }

    private boolean onMenuItem(MenuItem item) {
        if (current == null) return false;
        int id = item.getItemId();
        if (id == R.id.action_favorite) {
            vm.toggleFavorite();
            return true;
        }
        if (id == R.id.action_share) {
            Links.share(requireContext(), current.title, current.url);
            return true;
        }
        if (id == R.id.action_open) {
            Links.open(requireContext(), current.url);
            return true;
        }
        return false;
    }

    private void showMissing() {
        title.setText(R.string.article_missing);
        summary.setText(null);
        openOriginal.setVisibility(View.GONE);
    }

    private void bindArticle(ArticleEntity a) {
        float scale = c.settings.textScale();
        Category cat = Category.fromKey(a.primaryCategory, Category.WORLD);
        category.setText(CategoryUi.title(cat));
        title.setText(a.title);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f * scale);
        String when = TimeFormat.full(a.publishedAt, ZoneId.systemDefault());
        String metaText = a.sourceName + " · " + when;
        if (a.dateEstimated) metaText += " · " + getString(R.string.article_date_estimated);
        if (a.author != null && !a.author.isEmpty() && !a.author.equals(a.sourceName)) {
            metaText += "\n" + a.author;
        }
        meta.setText(metaText);
        badge.setVisibility(a.isDemo ? View.VISIBLE : View.GONE);

        boolean images = Network.shouldLoadImages(requireContext(), c.settings.imagePolicy());
        if (images && a.imageUrl != null && !a.imageUrl.isEmpty()) {
            image.setVisibility(View.VISIBLE);
            Images.load(image, a.imageUrl);
        } else {
            image.setVisibility(View.GONE);
        }
        if (a.summary.isEmpty()) {
            summary.setText(R.string.article_no_summary);
        } else {
            summary.setText(a.summary);
        }
        summary.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f * scale);
        openOriginal.setVisibility(a.url.isEmpty() ? View.GONE : View.VISIBLE);
        bindPolitics();
    }

    private void bindPolitics() {
        ArticleEntity a = current;
        if (a == null) return;
        boolean political = currentCluster != null ? currentCluster.political : a.political;
        if (!political && a.politicsOverride == 0) {
            politicsBox.setVisibility(View.GONE);
            return;
        }
        politicsBox.setVisibility(View.VISIBLE);
        long clusterId = a.clusterId;
        if (a.politicsOverride != 0) {
            politicsText.setText(a.politicsOverride > 0 ? R.string.politics_overridden_yes : R.string.politics_overridden_no);
            politicsToggle.setText(R.string.politics_reset);
            politicsToggle.setOnClickListener(v -> vm.setPoliticsOverride(clusterId, 0));
        } else {
            String matches = a.politicsMatches == null || a.politicsMatches.isEmpty() ? "—" : a.politicsMatches;
            politicsText.setText(getString(R.string.politics_reason, matches));
            politicsToggle.setText(R.string.politics_mark_not);
            politicsToggle.setOnClickListener(v -> vm.setPoliticsOverride(clusterId, -1));
        }
    }

    private void bindCluster(List<ArticleEntity> articles) {
        clusterList.removeAllViews();
        if (articles == null || articles.isEmpty()) {
            clusterHeader.setVisibility(View.GONE);
            clusterTimes.setVisibility(View.GONE);
            return;
        }
        Set<String> sources = new HashSet<>();
        long first = Long.MAX_VALUE;
        long last = Long.MIN_VALUE;
        for (ArticleEntity a : articles) {
            sources.add(a.sourceId);
            first = Math.min(first, a.publishedAt);
            last = Math.max(last, a.publishedAt);
        }
        clusterHeader.setVisibility(View.VISIBLE);
        clusterHeader.setText(getResources().getQuantityString(R.plurals.cluster_header, sources.size(), sources.size()));
        ZoneId zone = ZoneId.systemDefault();
        long now = System.currentTimeMillis();
        if (articles.size() > 1) {
            clusterTimes.setVisibility(View.VISIBLE);
            clusterTimes.setText(getString(R.string.cluster_times, TimeFormat.compact(first, now, zone),
                    TimeFormat.compact(last, now, zone)));
        } else {
            clusterTimes.setVisibility(View.GONE);
        }
        LayoutInflater inflater = getLayoutInflater();
        for (ArticleEntity a : articles) {
            View row = inflater.inflate(R.layout.item_cluster_source, clusterList, false);
            TextView source = row.findViewById(R.id.source);
            TextView rowTitle = row.findViewById(R.id.title);
            source.setText(getString(R.string.cluster_row_source, a.sourceName, TimeFormat.compact(a.publishedAt, now, zone)));
            rowTitle.setText(a.title);
            row.setContentDescription(getString(R.string.cluster_row_cd, a.sourceName, a.title));
            row.setOnClickListener(v -> Links.open(requireContext(), a.url));
            clusterList.addView(row);
        }
    }
}
