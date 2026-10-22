package com.newshub.app.ui.settings;

import android.content.Context;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.newshub.app.R;
import com.newshub.app.data.repo.SettingsRepository;
import com.newshub.app.domain.classify.PoliticsRules;
import com.newshub.app.domain.classify.Term;

import java.util.List;

/** Shows exactly which words the politics filter uses, with weights, so nothing is hidden blindly. */
public final class PoliticsRulesDialog {

    private PoliticsRulesDialog() {
    }

    public static void show(Context context, SettingsRepository settings) {
        PoliticsRules rules = settings.politicsRules();
        StringBuilder sb = new StringBuilder(context.getString(R.string.politics_rules_intro,
                rules.sensitivity().threshold));
        appendGroup(context, sb, rules, PoliticsRules.Group.DOMESTIC, R.string.politics_group_domestic);
        appendGroup(context, sb, rules, PoliticsRules.Group.GEO, R.string.politics_group_geo);
        appendGroup(context, sb, rules, PoliticsRules.Group.MILITARY, R.string.politics_group_military);
        String block = settings.getString(SettingsRepository.POLITICS_BLOCK);
        String allow = settings.getString(SettingsRepository.POLITICS_ALLOW);
        sb.append("\n\n").append(context.getString(R.string.politics_rules_user_block,
                block.isEmpty() ? "—" : block));
        sb.append("\n").append(context.getString(R.string.politics_rules_user_allow,
                allow.isEmpty() ? "—" : allow));
        sb.append("\n\n").append(context.getString(R.string.politics_rules_exceptions,
                String.join(", ", PoliticsRules.DEFAULT_EXCEPTIONS)));
        new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.pref_politics_rules)
                .setMessage(sb.toString())
                .setPositiveButton(R.string.action_close, null)
                .show();
    }

    private static void appendGroup(Context context, StringBuilder sb, PoliticsRules rules, PoliticsRules.Group group,
                                    int titleRes) {
        boolean on = rules.enabledGroups().contains(group);
        sb.append("\n\n").append(context.getString(titleRes))
                .append(on ? "" : " — " + context.getString(R.string.politics_group_off)).append('\n');
        List<Term> terms = PoliticsRules.defaultTerms(group);
        for (int weight = 3; weight >= 1; weight--) {
            StringBuilder line = new StringBuilder();
            for (Term t : terms) {
                if (t.weight != weight) continue;
                if (line.length() > 0) line.append(", ");
                line.append(t.display);
            }
            if (line.length() > 0) {
                sb.append(context.getString(R.string.politics_rules_weight, weight)).append(' ').append(line).append('\n');
            }
        }
    }
}
