package com.newshub.app;

import com.newshub.app.data.db.entity.SourceEntity;
import com.newshub.app.data.source.ProviderRegistry;
import com.newshub.app.data.source.ProviderResult;
import com.newshub.app.data.source.SourceProvider;
import com.newshub.app.domain.SourceType;
import com.newshub.app.domain.model.ParsedItem;

import java.util.ArrayList;
import java.util.List;

/**
 * Debug-only developer tools. Demo data is generated locally, is clearly labelled ("[Демо]"
 * prefix, "ДЕМО" badge, separate source) and never mixed with real news silently. This class does
 * not exist in release builds.
 */
public final class DevTools {

    private DevTools() {
    }

    static final String DEMO_A = "demo_a";
    static final String DEMO_B = "demo_b";

    public static boolean isAvailable() {
        return true;
    }

    public static void registerProviders(ProviderRegistry registry) {
        registry.register(new DemoProvider());
    }

    public static void setDemoEnabled(AppContainer c, boolean enabled, Runnable done) {
        c.io.execute(() -> {
            c.db.runInTransaction(() -> {
                if (enabled) {
                    c.db.sources().upsert(demoSource(DEMO_A, "Демо-источник A (разработка)", 1000));
                    c.db.sources().upsert(demoSource(DEMO_B, "Демо-источник B (разработка)", 1001));
                } else {
                    c.db.articles().deleteDemo();
                    c.db.history().deleteOrphans();
                    c.db.clusters().deleteOrphans();
                    c.db.sources().delete(DEMO_A);
                    c.db.sources().delete(DEMO_B);
                }
            });
            if (enabled) c.news.refreshBlocking(true);
            if (done != null) done.run();
        });
    }

    private static SourceEntity demoSource(String id, String name, int order) {
        SourceEntity s = new SourceEntity();
        s.id = id;
        s.name = name;
        s.type = SourceType.DEMO.name();
        s.address = "demo://" + id;
        s.category = "tech";
        s.enabled = true;
        s.builtIn = false;
        s.verification = "UNVERIFIED";
        s.note = "Сгенерированные данные для проверки интерфейса. Не являются новостями.";
        s.sortOrder = order;
        s.createdAt = System.currentTimeMillis();
        return s;
    }

    /** Generates labelled demo items; the two demo sources share some topics to show grouping. */
    static final class DemoProvider implements SourceProvider {

        private static final String[] TOPICS = {
                "Пример: вышло обновление вымышленной операционной системы «Демо ОС 3»",
                "Пример: исследователи показали вымышленную нейросеть «Демо-модель»",
                "Пример: вымышленный телескоп «Демо-1» сделал первый снимок",
                "Пример: анонсирована вымышленная игра «Демо Квест»",
                "Пример: в прокат выходит вымышленный фильм «Демо-фильм»",
                "Пример: курс вымышленной валюты «демо-кредит» не изменился",
                "Пример: вымышленная команда «Демо» выиграла товарищеский матч",
                "Пример: учёные дали вымышленный совет о пользе сна",
        };

        @Override
        public SourceType type() {
            return SourceType.DEMO;
        }

        @Override
        public ProviderResult fetch(SourceEntity source, boolean conditional, long now) {
            List<ParsedItem> items = new ArrayList<>();
            boolean second = DEMO_B.equals(source.id);
            for (int i = 0; i < TOPICS.length; i++) {
                if (second && i % 2 == 1) continue;
                ParsedItem it = new ParsedItem();
                it.guid = source.id + "-" + i;
                it.title = "[Демо] " + TOPICS[i] + (second ? " — подробности" : "");
                it.summary = "Это демонстрационная публикация, созданная приложением в режиме разработки. "
                        + "Она нужна для проверки интерфейса и не является новостью.";
                it.link = "https://example.com/demo/" + i;
                it.publishedAt = now - (i * 47L + (second ? 20 : 0)) * 60_000L;
                items.add(it);
            }
            return ProviderResult.ok(items, source.name, null, null, null);
        }
    }
}
