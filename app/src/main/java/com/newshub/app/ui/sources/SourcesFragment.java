package com.newshub.app.ui.sources;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.newshub.app.AppContainer;
import com.newshub.app.NewsHubApp;
import com.newshub.app.R;
import com.newshub.app.data.db.entity.SourceEntity;
import com.newshub.app.data.db.model.SourceCount;
import com.newshub.app.domain.Category;
import com.newshub.app.ui.common.CategoryUi;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Source management: grouped list, status, enable/disable, add/edit/remove, refresh. */
public class SourcesFragment extends Fragment implements SourceAdapter.Listener {

    private AppContainer c;
    private SourcesViewModel vm;
    private SourceAdapter adapter;
    private TextView summary;
    private List<SourceEntity> sources = new ArrayList<>();
    private final Map<String, Integer> counts = new HashMap<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_sources, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        c = NewsHubApp.container(requireContext());
        vm = new ViewModelProvider(this).get(SourcesViewModel.class);
        summary = view.findViewById(R.id.summary);
        LinearProgressIndicator progress = view.findViewById(R.id.progress);
        RecyclerView list = view.findViewById(R.id.list);
        list.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new SourceAdapter(this);
        list.setAdapter(adapter);

        MaterialToolbar toolbar = view.findViewById(R.id.toolbar);
        toolbar.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.action_refresh) {
                vm.refreshAll();
                return true;
            }
            if (id == R.id.action_reset) {
                new MaterialAlertDialogBuilder(requireContext())
                        .setTitle(R.string.sources_reset)
                        .setMessage(R.string.sources_reset_confirm)
                        .setPositiveButton(R.string.action_reset, (d, w) -> vm.resetBuiltIns(null))
                        .setNegativeButton(R.string.action_cancel, null)
                        .show();
                return true;
            }
            if (id == R.id.action_about) {
                new MaterialAlertDialogBuilder(requireContext())
                        .setTitle(R.string.sources_about)
                        .setMessage(R.string.sources_about_text)
                        .setPositiveButton(R.string.action_close, null)
                        .show();
                return true;
            }
            return false;
        });
        view.findViewById(R.id.add).setOnClickListener(v ->
                SourceEditSheet.newInstance(null).show(getChildFragmentManager(), "edit"));

        vm.sources.observe(getViewLifecycleOwner(), list1 -> {
            sources = list1 == null ? new ArrayList<>() : list1;
            rebuild();
        });
        vm.counts.observe(getViewLifecycleOwner(), list2 -> {
            counts.clear();
            if (list2 != null) {
                for (SourceCount sc : list2) counts.put(sc.sourceId, sc.count);
            }
            rebuild();
        });
        c.news.refreshState().observe(getViewLifecycleOwner(), s ->
                progress.setVisibility(s != null && s.running ? View.VISIBLE : View.GONE));
    }

    private void rebuild() {
        List<SourceAdapter.Row> rows = new ArrayList<>();
        int active = 0;
        int failing = 0;
        List<SourceEntity> other = new ArrayList<>();
        for (Category cat : Category.assignable()) {
            List<SourceEntity> group = new ArrayList<>();
            for (SourceEntity s : sources) {
                if (cat.key.equals(s.category)) group.add(s);
            }
            if (group.isEmpty()) continue;
            rows.add(SourceAdapter.Row.header(getString(CategoryUi.title(cat))));
            for (SourceEntity s : group) {
                rows.add(SourceAdapter.Row.source(s, countOf(s.id)));
            }
        }
        for (SourceEntity s : sources) {
            if (Category.fromKey(s.category, null) == null || Category.MAIN.key.equals(s.category)) other.add(s);
            if (s.enabled && !s.address.isEmpty()) {
                active++;
                if (s.lastErrorKind != null) failing++;
            }
        }
        if (!other.isEmpty()) {
            rows.add(SourceAdapter.Row.header(getString(R.string.sources_other)));
            for (SourceEntity s : other) rows.add(SourceAdapter.Row.source(s, countOf(s.id)));
        }
        adapter.submitList(rows);
        String text = getString(R.string.sources_summary, active, sources.size());
        if (failing > 0) {
            text += " · " + getResources().getQuantityString(R.plurals.sources_failing, failing, failing);
        }
        if (!c.settings.telegramEnabled()) text += " · " + getString(R.string.sources_telegram_off);
        summary.setText(text);
    }

    private int countOf(String id) {
        Integer n = counts.get(id);
        return n == null ? 0 : n;
    }

    @Override
    public void onEdit(SourceEntity source) {
        SourceEditSheet.newInstance(source.id).show(getChildFragmentManager(), "edit");
    }

    @Override
    public void onToggle(SourceEntity source, boolean enabled) {
        if (source.enabled != enabled) vm.setEnabled(source.id, enabled);
    }
}
