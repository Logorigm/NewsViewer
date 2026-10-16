package com.newshub.app.ui.sources;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.materialswitch.MaterialSwitch;
import com.newshub.app.R;
import com.newshub.app.data.db.entity.SourceEntity;
import com.newshub.app.domain.SourceType;
import com.newshub.app.domain.text.TimeFormat;
import com.newshub.app.ui.common.ErrorText;

import java.time.ZoneId;
import java.util.Objects;

/** Sources grouped by category, with live status and an on/off switch. */
public class SourceAdapter extends ListAdapter<SourceAdapter.Row, RecyclerView.ViewHolder> {

    public interface Listener {
        void onEdit(SourceEntity source);

        void onToggle(SourceEntity source, boolean enabled);
    }

    /** Either a section header or a source with its stored article count. */
    public static final class Row {
        final String header;
        final SourceEntity source;
        final int articleCount;

        private Row(String header, SourceEntity source, int articleCount) {
            this.header = header;
            this.source = source;
            this.articleCount = articleCount;
        }

        public static Row header(String title) {
            return new Row(title, null, 0);
        }

        public static Row source(SourceEntity s, int count) {
            return new Row(null, s, count);
        }

        String key() {
            return header != null ? "h:" + header : "s:" + source.id;
        }
    }

    private static final int TYPE_HEADER = 0;
    private static final int TYPE_SOURCE = 1;

    private static final DiffUtil.ItemCallback<Row> DIFF = new DiffUtil.ItemCallback<Row>() {
        @Override
        public boolean areItemsTheSame(@NonNull Row a, @NonNull Row b) {
            return a.key().equals(b.key());
        }

        @Override
        public boolean areContentsTheSame(@NonNull Row a, @NonNull Row b) {
            if (a.header != null) return a.header.equals(b.header);
            SourceEntity x = a.source;
            SourceEntity y = b.source;
            return a.articleCount == b.articleCount && x.enabled == y.enabled && x.lastFetchAt == y.lastFetchAt
                    && x.lastSuccessAt == y.lastSuccessAt && Objects.equals(x.name, y.name)
                    && Objects.equals(x.address, y.address) && Objects.equals(x.lastErrorKind, y.lastErrorKind)
                    && Objects.equals(x.lastError, y.lastError) && Objects.equals(x.category, y.category);
        }
    };

    private final Listener listener;

    public SourceAdapter(Listener listener) {
        super(DIFF);
        this.listener = listener;
    }

    @Override
    public int getItemViewType(int position) {
        return getItem(position).header != null ? TYPE_HEADER : TYPE_SOURCE;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_HEADER) {
            return new HeaderHolder(inflater.inflate(R.layout.item_section_header, parent, false));
        }
        return new SourceHolder(inflater.inflate(R.layout.item_source, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Row row = getItem(position);
        if (holder instanceof HeaderHolder) {
            ((HeaderHolder) holder).text.setText(row.header);
        } else {
            ((SourceHolder) holder).bind(row);
        }
    }

    static final class HeaderHolder extends RecyclerView.ViewHolder {
        final TextView text;

        HeaderHolder(View v) {
            super(v);
            text = v.findViewById(R.id.header);
        }
    }

    final class SourceHolder extends RecyclerView.ViewHolder {
        final ImageView icon;
        final TextView name;
        final TextView status;
        final MaterialSwitch enabled;

        SourceHolder(View v) {
            super(v);
            icon = v.findViewById(R.id.type_icon);
            name = v.findViewById(R.id.name);
            status = v.findViewById(R.id.status);
            enabled = v.findViewById(R.id.enabled);
        }

        void bind(Row row) {
            SourceEntity s = row.source;
            Context ctx = itemView.getContext();
            SourceType type = SourceType.parse(s.type, SourceType.RSS);
            icon.setImageResource(type == SourceType.TELEGRAM ? R.drawable.ic_send
                    : type == SourceType.DEMO ? R.drawable.ic_info : R.drawable.ic_sources);
            name.setText(s.name);
            boolean error = false;
            String text;
            String typeLabel = ctx.getString(type == SourceType.TELEGRAM ? R.string.source_type_telegram_short
                    : type == SourceType.DEMO ? R.string.source_type_demo : R.string.source_type_rss_short);
            long now = System.currentTimeMillis();
            if (s.address.isEmpty()) {
                text = ctx.getString(R.string.source_status_needs_setup);
                error = true;
            } else if (!s.enabled) {
                text = ctx.getString(R.string.source_status_disabled);
            } else if (s.lastErrorKind != null) {
                text = ErrorText.describe(ctx, s.lastErrorKind, null) + " · "
                        + TimeFormat.relative(s.lastFetchAt, now, ZoneId.systemDefault());
                error = true;
            } else if (s.lastSuccessAt > 0) {
                text = ctx.getString(R.string.source_status_ok,
                        TimeFormat.relative(s.lastSuccessAt, now, ZoneId.systemDefault()),
                        ctx.getResources().getQuantityString(R.plurals.publications, row.articleCount, row.articleCount));
            } else {
                text = ctx.getString(R.string.source_status_never);
            }
            status.setText(typeLabel + " · " + text);
            status.setTextColor(ContextCompat.getColor(ctx, error ? R.color.nh_error : R.color.nh_text_tertiary));

            enabled.setOnCheckedChangeListener(null);
            enabled.setChecked(s.enabled);
            enabled.setEnabled(!s.address.isEmpty());
            enabled.setContentDescription(ctx.getString(R.string.source_toggle_cd, s.name));
            enabled.setOnCheckedChangeListener((b, checked) -> listener.onToggle(s, checked));
            itemView.setOnClickListener(v -> listener.onEdit(s));
        }
    }
}
