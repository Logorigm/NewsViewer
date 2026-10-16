package com.newshub.app.ui.sources;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.newshub.app.AppContainer;
import com.newshub.app.NewsHubApp;
import com.newshub.app.R;
import com.newshub.app.data.db.entity.SourceEntity;
import com.newshub.app.data.source.ProviderResult;
import com.newshub.app.domain.Category;
import com.newshub.app.domain.SourceType;
import com.newshub.app.domain.VerificationLevel;
import com.newshub.app.domain.model.ParsedItem;
import com.newshub.app.domain.text.TimeFormat;
import com.newshub.app.ui.common.CategoryUi;
import com.newshub.app.ui.common.ErrorText;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/** Add or edit a source: type, address, name, category; "check" fetches it once without saving. */
public class SourceEditSheet extends BottomSheetDialogFragment {

    private static final String ARG_ID = "sourceId";

    private final Handler main = new Handler(Looper.getMainLooper());
    private AppContainer c;
    private String sourceId;
    private boolean filled;
    private final List<Category> categories = new ArrayList<>(Category.assignable());
    private int selectedCategory = categories.indexOf(Category.TECH);

    private TextView sheetTitle;
    private MaterialButtonToggleGroup typeGroup;
    private TextInputLayout addressLayout;
    private TextInputEditText address;
    private TextInputEditText name;
    private MaterialAutoCompleteTextView category;
    private TextView info;
    private LinearProgressIndicator testProgress;
    private TextView testResult;
    private MaterialButton save;
    private MaterialButton test;
    private MaterialButton delete;

    public static SourceEditSheet newInstance(@Nullable String sourceId) {
        SourceEditSheet sheet = new SourceEditSheet();
        Bundle args = new Bundle();
        args.putString(ARG_ID, sourceId);
        sheet.setArguments(args);
        return sheet;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.sheet_source_edit, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        c = NewsHubApp.container(requireContext());
        sourceId = requireArguments().getString(ARG_ID);
        sheetTitle = view.findViewById(R.id.sheet_title);
        typeGroup = view.findViewById(R.id.type_group);
        addressLayout = view.findViewById(R.id.address_layout);
        address = view.findViewById(R.id.address);
        name = view.findViewById(R.id.name);
        category = view.findViewById(R.id.category);
        info = view.findViewById(R.id.info);
        testProgress = view.findViewById(R.id.test_progress);
        testResult = view.findViewById(R.id.test_result);
        save = view.findViewById(R.id.save);
        test = view.findViewById(R.id.test);
        delete = view.findViewById(R.id.delete);

        List<String> names = new ArrayList<>();
        for (Category cat : categories) names.add(getString(CategoryUi.title(cat)));
        category.setAdapter(new ArrayAdapter<>(requireContext(), android.R.layout.simple_list_item_1, names));
        category.setOnItemClickListener((parent, v, position, id) -> selectedCategory = position);
        category.setText(names.get(selectedCategory), false);

        typeGroup.check(R.id.type_rss);
        typeGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) updateAddressHint();
        });
        updateAddressHint();

        if (sourceId == null) {
            sheetTitle.setText(R.string.source_add_title);
            info.setText(R.string.source_add_info);
            delete.setVisibility(View.GONE);
        } else {
            sheetTitle.setText(R.string.source_edit_title);
            delete.setVisibility(View.VISIBLE);
            c.sources.observe(sourceId).observe(getViewLifecycleOwner(), this::fill);
        }

        test.setOnClickListener(v -> runTest());
        save.setOnClickListener(v -> runSave());
        delete.setOnClickListener(v -> new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.source_delete)
                .setMessage(R.string.source_delete_confirm)
                .setPositiveButton(R.string.action_delete, (d, w) -> c.sources.delete(sourceId,
                        () -> main.post(() -> {
                            if (isAdded()) dismiss();
                        })))
                .setNegativeButton(R.string.action_cancel, null)
                .show());
    }

    private SourceType selectedType() {
        return typeGroup.getCheckedButtonId() == R.id.type_telegram ? SourceType.TELEGRAM : SourceType.RSS;
    }

    private void updateAddressHint() {
        boolean tg = selectedType() == SourceType.TELEGRAM;
        addressLayout.setHint(tg ? R.string.source_address_hint_telegram : R.string.source_address_hint_rss);
        addressLayout.setHelperText(getString(tg ? R.string.source_address_help_telegram
                : R.string.source_address_help_rss));
        addressLayout.setError(null);
    }

    private void fill(SourceEntity s) {
        if (s == null) {
            if (isAdded()) dismiss();
            return;
        }
        if (!filled) {
            filled = true;
            name.setText(s.name);
            address.setText(s.address);
            SourceType type = SourceType.parse(s.type, SourceType.RSS);
            typeGroup.check(type == SourceType.TELEGRAM ? R.id.type_telegram : R.id.type_rss);
            Category cat = Category.fromKey(s.category, Category.WORLD);
            int idx = categories.indexOf(cat);
            if (idx >= 0) {
                selectedCategory = idx;
                category.setText(getString(CategoryUi.title(cat)), false);
            }
            if (type == SourceType.DEMO) {
                typeGroup.setEnabled(false);
                address.setEnabled(false);
                save.setEnabled(false);
                test.setEnabled(false);
            }
        }
        info.setText(describe(s));
    }

    private String describe(SourceEntity s) {
        StringBuilder sb = new StringBuilder();
        VerificationLevel level = VerificationLevel.parse(s.verification);
        if (s.builtIn) {
            sb.append(getString(R.string.source_verification_label)).append(": ")
                    .append(getString(verificationText(level)));
        }
        if (s.note != null && !s.note.isEmpty()) sb.append('\n').append(s.note);
        if (s.discoveredFeedUrl != null && !s.discoveredFeedUrl.isEmpty()) {
            sb.append('\n').append(getString(R.string.source_discovered, s.discoveredFeedUrl));
        }
        long now = System.currentTimeMillis();
        ZoneId zone = ZoneId.systemDefault();
        if (s.lastErrorKind != null) {
            sb.append('\n').append(getString(R.string.source_last_error,
                    ErrorText.describe(requireContext(), s.lastErrorKind, s.lastError),
                    TimeFormat.relative(s.lastFetchAt, now, zone)));
        } else if (s.lastSuccessAt > 0) {
            sb.append('\n').append(getString(R.string.source_last_ok, TimeFormat.relative(s.lastSuccessAt, now, zone),
                    s.lastItemCount));
        }
        if (s.nextAllowedFetchAt > now) {
            sb.append('\n').append(getString(R.string.source_backoff, TimeFormat.compact(s.nextAllowedFetchAt, now, zone)));
        }
        return sb.toString().trim();
    }

    private static int verificationText(VerificationLevel level) {
        switch (level) {
            case CONFIRMED:
                return R.string.verification_confirmed;
            case KNOWN:
                return R.string.verification_known;
            case DISCOVERY:
                return R.string.verification_discovery;
            case UNVERIFIED:
            default:
                return R.string.verification_unverified;
        }
    }

    private String text(TextInputEditText field) {
        return field.getText() == null ? "" : field.getText().toString().trim();
    }

    private void runTest() {
        addressLayout.setError(null);
        testProgress.setVisibility(View.VISIBLE);
        testResult.setVisibility(View.GONE);
        test.setEnabled(false);
        c.sources.testAsync(selectedType(), text(address), sourceId,
                error -> main.post(() -> {
                    if (!isAdded()) return;
                    testProgress.setVisibility(View.GONE);
                    test.setEnabled(true);
                    addressLayout.setError(error);
                }),
                result -> main.post(() -> {
                    if (!isAdded()) return;
                    testProgress.setVisibility(View.GONE);
                    test.setEnabled(true);
                    showTestResult(result);
                }));
    }

    private void showTestResult(ProviderResult r) {
        testResult.setVisibility(View.VISIBLE);
        if (r.status == ProviderResult.Status.ERROR) {
            testResult.setText(getString(R.string.source_test_failed,
                    ErrorText.describe(requireContext(), r.errorKind == null ? null : r.errorKind.name(), r.message)));
            return;
        }
        StringBuilder sb = new StringBuilder(getResources().getQuantityString(R.plurals.source_test_ok, r.items.size(),
                r.items.size()));
        if (!r.items.isEmpty()) {
            ParsedItem first = r.items.get(0);
            sb.append('\n').append(getString(R.string.source_test_latest, first.title));
        }
        if (r.discoveredFeedUrl != null) {
            sb.append('\n').append(getString(R.string.source_discovered, r.discoveredFeedUrl));
        }
        if (text(name).isEmpty() && r.feedTitle != null && !r.feedTitle.isEmpty()) {
            name.setText(r.feedTitle);
        }
        testResult.setText(sb);
    }

    private void runSave() {
        addressLayout.setError(null);
        save.setEnabled(false);
        String categoryKey = categories.get(Math.max(0, selectedCategory)).key;
        c.sources.save(sourceId, text(name), selectedType(), text(address), categoryKey,
                error -> main.post(() -> {
                    if (!isAdded()) return;
                    save.setEnabled(true);
                    if (error != null) {
                        addressLayout.setError(error);
                    } else {
                        dismiss();
                    }
                }));
    }
}
