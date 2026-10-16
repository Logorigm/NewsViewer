package com.newshub.app.ui.feed;

import android.content.Context;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.newshub.app.R;
import com.newshub.app.data.db.model.FeedRow;
import com.newshub.app.domain.Category;
import com.newshub.app.domain.text.TimeFormat;
import com.newshub.app.ui.common.CategoryUi;
import com.newshub.app.ui.common.Dimens;
import com.newshub.app.ui.common.Images;

import java.time.ZoneId;
import java.util.Locale;

/** News cards shared by the feed, category, search, favorites and history screens. */
public class FeedAdapter extends ListAdapter<FeedItem, FeedAdapter.Holder> {

    public interface Listener {
        void onOpen(FeedItem item);

        void onToggleFavorite(FeedItem item);

        void onWhy(FeedItem item);
    }

    private static final DiffUtil.ItemCallback<FeedItem> DIFF = new DiffUtil.ItemCallback<FeedItem>() {
        @Override
        public boolean areItemsTheSame(@NonNull FeedItem a, @NonNull FeedItem b) {
            return a.key() == b.key() && a.grouped == b.grouped;
        }

        @Override
        public boolean areContentsTheSame(@NonNull FeedItem a, @NonNull FeedItem b) {
            return a.sameContent(b);
        }
    };

    private final Listener listener;
    private float textScale = 1f;
    private boolean loadImages = true;
    private boolean politicalReasons;

    public FeedAdapter(Listener listener) {
        super(DIFF);
        this.listener = listener;
    }

    /** Text size and image policy from the settings. */
    public void setDisplayOptions(float scale, boolean images) {
        if (scale != textScale || images != loadImages) {
            textScale = scale;
            loadImages = images;
            notifyDataSetChanged();
        }
    }

    /** On the "hidden publications" screen every card explains why it was hidden. */
    public void setShowPoliticalReasons(boolean show) {
        politicalReasons = show;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_feed_card, parent, false);
        return new Holder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        holder.bind(getItem(position));
    }

    @Override
    public void onViewRecycled(@NonNull Holder holder) {
        Images.clear(holder.image);
    }

    final class Holder extends RecyclerView.ViewHolder {
        final View card;
        final TextView avatar;
        final TextView sourceName;
        final TextView time;
        final TextView badge;
        final TextView title;
        final TextView summary;
        final ImageView image;
        final TextView category;
        final TextView sourcesCount;
        final TextView why;
        final ImageButton favorite;

        Holder(View v) {
            super(v);
            card = v.findViewById(R.id.card);
            avatar = v.findViewById(R.id.source_avatar);
            sourceName = v.findViewById(R.id.source_name);
            time = v.findViewById(R.id.time);
            badge = v.findViewById(R.id.badge);
            title = v.findViewById(R.id.title);
            summary = v.findViewById(R.id.summary);
            image = v.findViewById(R.id.image);
            category = v.findViewById(R.id.category);
            sourcesCount = v.findViewById(R.id.sources_count);
            why = v.findViewById(R.id.why);
            favorite = v.findViewById(R.id.favorite);
        }

        void bind(FeedItem item) {
            Context ctx = itemView.getContext();
            FeedRow r = item.row;
            String source = r.sourceName == null ? "" : r.sourceName;
            avatar.setText(source.isEmpty() ? "·" : source.substring(0, 1).toUpperCase(Locale.ROOT));
            sourceName.setText(source);
            String when = TimeFormat.relative(item.displayTime(), System.currentTimeMillis(), ZoneId.systemDefault());
            time.setText(ctx.getString(R.string.card_time, when));

            if (r.isDemo) {
                badge.setVisibility(View.VISIBLE);
                badge.setText(R.string.badge_demo);
                badge.setBackgroundResource(R.drawable.bg_badge_warning);
                badge.setTextColor(ContextCompat.getColor(ctx, R.color.nh_warning));
            } else if (r.political) {
                badge.setVisibility(View.VISIBLE);
                badge.setText(R.string.badge_politics);
                badge.setBackgroundResource(R.drawable.bg_badge);
                badge.setTextColor(ContextCompat.getColor(ctx, R.color.nh_text_secondary));
            } else {
                badge.setVisibility(View.GONE);
            }

            title.setText(r.title);
            title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f * textScale);
            title.setAlpha(r.isRead ? 0.62f : 1f);
            if (r.summary == null || r.summary.isEmpty()) {
                summary.setVisibility(View.GONE);
            } else {
                summary.setVisibility(View.VISIBLE);
                summary.setText(r.summary);
                summary.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f * textScale);
            }

            if (loadImages && r.imageUrl != null && !r.imageUrl.isEmpty()) {
                image.setVisibility(View.VISIBLE);
                Images.load(image, r.imageUrl);
            } else {
                Images.clear(image);
                image.setVisibility(View.GONE);
            }

            Category c = Category.fromKey(r.primaryCategory, Category.WORLD);
            category.setText(CategoryUi.title(c));
            category.setCompoundDrawablesRelative(CategoryUi.dot(ctx, c), null, null, null);

            if (r.sourceCount >= 2) {
                sourcesCount.setVisibility(View.VISIBLE);
                sourcesCount.setText(ctx.getResources().getQuantityString(R.plurals.sources_count, r.sourceCount,
                        r.sourceCount));
                sourcesCount.setCompoundDrawablesRelative(
                        Dimens.smallIcon(ctx, R.drawable.ic_layers, 14, R.color.nh_text_tertiary), null, null, null);
            } else {
                sourcesCount.setVisibility(View.GONE);
            }

            boolean showWhy = item.reasons != null || (politicalReasons && r.political);
            why.setVisibility(showWhy ? View.VISIBLE : View.GONE);
            why.setText(item.reasons != null ? R.string.why_button : R.string.why_hidden_button);
            why.setOnClickListener(v -> listener.onWhy(item));

            favorite.setImageResource(r.isFavorite ? R.drawable.ic_bookmark : R.drawable.ic_bookmark_border);
            favorite.setContentDescription(ctx.getString(r.isFavorite
                    ? R.string.action_remove_favorite : R.string.action_add_favorite));
            favorite.setOnClickListener(v -> listener.onToggleFavorite(item));

            card.setOnClickListener(v -> listener.onOpen(item));
            StringBuilder cd = new StringBuilder();
            cd.append(source).append(", ").append(when).append(". ").append(r.title);
            if (r.sourceCount >= 2) {
                cd.append(". ").append(sourcesCount.getText());
            }
            if (r.political) cd.append(". ").append(ctx.getString(R.string.badge_politics));
            card.setContentDescription(cd);
        }
    }
}
