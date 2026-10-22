package com.newshub.app.ui.feed;

import android.content.Context;

import com.newshub.app.R;
import com.newshub.app.domain.rank.InterestRanker;
import com.newshub.app.ui.common.CategoryUi;

import java.util.List;

/** Human-readable explanation of why a story was picked for "Только интересное". */
public final class ReasonFormatter {

    private ReasonFormatter() {
    }

    public static String format(Context context, List<InterestRanker.Reason> reasons) {
        StringBuilder sb = new StringBuilder();
        for (InterestRanker.Reason r : reasons) {
            String topic = context.getString(CategoryUi.title(r.category));
            String line;
            switch (r.type) {
                case FRESH:
                    line = r.value < 1 ? context.getString(R.string.reason_fresh_now)
                            : context.getResources().getQuantityString(R.plurals.reason_fresh_hours, r.value, r.value);
                    break;
                case MULTI_SOURCE:
                    line = context.getResources().getQuantityString(R.plurals.reason_multi_source, r.value, r.value);
                    break;
                case LIKED_TOPIC:
                    line = context.getString(R.string.reason_liked_topic, topic);
                    break;
                case READ_OFTEN:
                    line = context.getString(R.string.reason_read_often, topic);
                    break;
                case DEFAULT_TOPIC:
                default:
                    line = context.getString(R.string.reason_default_topic, topic);
                    break;
            }
            sb.append("• ").append(line).append('\n');
        }
        sb.append('\n').append(context.getString(R.string.reason_footer));
        return sb.toString();
    }
}
