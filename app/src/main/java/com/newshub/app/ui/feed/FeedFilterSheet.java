package com.newshub.app.ui.feed;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.newshub.app.AppContainer;
import com.newshub.app.NewsHubApp;
import com.newshub.app.R;
import com.newshub.app.data.db.entity.SourceEntity;
import com.newshub.app.domain.feed.FeedFilter;
import com.newshub.app.domain.feed.Period;
import com.newshub.app.domain.feed.SortOrder;

import java.util.ArrayList;
import java.util.List;

/** Period, sort order and source filter for a feed screen. */
public class FeedFilterSheet extends BottomSheetDialogFragment {

    private final List<String> sourceIds = new ArrayList<>();
    private int selectedSource;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.sheet_feed_filter, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        AppContainer c = NewsHubApp.container(requireContext());
        FeedViewModel vm = new ViewModelProvider(requireParentFragment()).get(FeedViewModel.class);
        FeedFilter f = vm.currentFilter();

        ChipGroup periods = view.findViewById(R.id.period_chips);
        periods.check(periodChip(f.period));
        ChipGroup sorts = view.findViewById(R.id.sort_chips);
        sorts.check(f.sort == SortOrder.MOST_SOURCES ? R.id.sort_sources : R.id.sort_newest);

        MaterialAutoCompleteTextView source = view.findViewById(R.id.source);
        source.setOnItemClickListener((parent, v, position, id) -> selectedSource = position);
        c.sources.observeSources().observe(getViewLifecycleOwner(), list -> {
            List<String> names = new ArrayList<>();
            sourceIds.clear();
            names.add(getString(R.string.filters_all_sources));
            sourceIds.add(null);
            selectedSource = 0;
            for (SourceEntity s : list) {
                if (!s.enabled) continue;
                if (s.id.equals(f.sourceId)) selectedSource = names.size();
                names.add(s.name);
                sourceIds.add(s.id);
            }
            source.setAdapter(new ArrayAdapter<>(requireContext(), android.R.layout.simple_list_item_1, names));
            source.setText(names.get(selectedSource), false);
        });

        view.findViewById(R.id.apply).setOnClickListener(v -> {
            String sourceId = selectedSource >= 0 && selectedSource < sourceIds.size() ? sourceIds.get(selectedSource) : null;
            SortOrder sort = sorts.getCheckedChipId() == R.id.sort_sources ? SortOrder.MOST_SOURCES : SortOrder.NEWEST;
            vm.applyFilter(periodFromChip(periods.getCheckedChipId()), sort, sourceId);
            dismiss();
        });
        view.findViewById(R.id.reset).setOnClickListener(v -> {
            vm.resetFilter();
            dismiss();
        });
    }

    private static int periodChip(Period p) {
        switch (p) {
            case DAY:
                return R.id.period_day;
            case MONTH:
                return R.id.period_month;
            case YEAR:
                return R.id.period_year;
            case ALL:
                return R.id.period_all;
            case WEEK:
            default:
                return R.id.period_week;
        }
    }

    private static Period periodFromChip(int id) {
        if (id == R.id.period_day) return Period.DAY;
        if (id == R.id.period_month) return Period.MONTH;
        if (id == R.id.period_year) return Period.YEAR;
        if (id == R.id.period_all) return Period.ALL;
        return Period.WEEK;
    }
}
