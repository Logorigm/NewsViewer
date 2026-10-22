package com.newshub.app.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.newshub.app.domain.classify.Classification;
import com.newshub.app.domain.classify.PoliticsResult;
import com.newshub.app.domain.classify.PoliticsRules;
import com.newshub.app.domain.classify.PoliticsRules.Group;
import com.newshub.app.domain.classify.PoliticsRules.Sensitivity;
import com.newshub.app.domain.classify.TopicClassifier;

import org.junit.Test;

import java.util.Collections;
import java.util.EnumSet;

public class TopicClassifierTest {

    private final TopicClassifier classifier = new TopicClassifier(PoliticsRules.defaults());

    private Classification classify(String title, String summary, Category source) {
        return classifier.classify(title, summary, Collections.emptyList(), source);
    }

    @Test
    public void scienceFromGeneralSource() {
        Classification c = classify("Учёные обнаружили новую экзопланету с помощью телескопа", "", Category.WORLD);
        assertEquals(Category.SCIENCE, c.primary);
    }

    @Test
    public void aiBeatsTechForModelNews() {
        Classification c = classify("OpenAI представила новую языковую модель", "", Category.TECH);
        assertEquals(Category.AI, c.primary);
    }

    @Test
    public void sportWithSecondaryCategory() {
        Classification c = classify("Сборная России по хоккею выиграла матч", "", Category.WORLD);
        assertEquals(Category.SPORT, c.primary);
        assertTrue(c.categories.contains(Category.WORLD));
        assertTrue((c.categoryMask & Category.SPORT.mask()) != 0);
    }

    @Test
    public void sourceCategoryIsFallback() {
        Classification c = classify("Вышел большой патч с исправлениями", "", Category.GAMES);
        assertEquals(Category.GAMES, c.primary);
    }

    @Test
    public void sourceTagsHelp() {
        Classification c = classifier.classify("Итоги недели", "", Collections.singletonList("Футбол"), Category.WORLD);
        assertEquals(Category.SPORT, c.primary);
    }

    @Test
    public void stateRelatedNewsIsNotAutomaticallyPolitical() {
        assertFalse(classify("Губернатор открыл новую школу в Тюмени", "", Category.WORLD).politics.political);
        assertFalse(classify("Правительство утвердило новые правила льготной ипотеки", "", Category.ECONOMY).politics.political);
        assertFalse(classify("Роскосмос запустил спутник на орбиту", "", Category.SCIENCE).politics.political);
        assertFalse(classify("Президент Сбербанка рассказал о новых вкладах", "", Category.ECONOMY).politics.political);
    }

    @Test
    public void clearlyPoliticalNews() {
        PoliticsResult p = classify("Госдума приняла законопроект о маркетплейсах", "", Category.WORLD).politics;
        assertTrue(p.political);
        assertTrue(p.matches.contains("госдум"));
        assertTrue(p.matches.contains("законопроект"));
        assertTrue(classify("Лавров провёл переговоры с генсеком ООН", "", Category.WORLD).politics.political);
    }

    @Test
    public void exceptionsRemoveFalsePositives() {
        PoliticsResult p = classify("Новый сериал по вселенной «Звёздные войны» выйдет в декабре", "",
                Category.ENTERTAINMENT).politics;
        assertFalse(p.political);
        assertEquals(0, p.score);
    }

    @Test
    public void groupsCanBeDisabled() {
        String title = "Минобороны сообщило о сбитых беспилотниках";
        assertTrue(classify(title, "", Category.WORLD).politics.political);
        TopicClassifier noMilitary = new TopicClassifier(new PoliticsRules(
                EnumSet.of(Group.DOMESTIC, Group.GEO), Sensitivity.NORMAL, "", ""));
        assertFalse(noMilitary.classify(title, "", null, Category.WORLD).politics.political);
    }

    @Test
    public void sensitivityChangesThreshold() {
        String title = "Губернатор открыл новую школу в Тюмени";
        TopicClassifier strict = new TopicClassifier(new PoliticsRules(
                EnumSet.allOf(Group.class), Sensitivity.STRICT, "", ""));
        assertTrue(strict.classify(title, "", null, Category.WORLD).politics.political);
        TopicClassifier soft = new TopicClassifier(new PoliticsRules(
                EnumSet.allOf(Group.class), Sensitivity.SOFT, "", ""));
        assertFalse(soft.classify("Песков прокомментировал новости", "", null, Category.WORLD).politics.political);
    }

    @Test
    public void userBlockAndAllowWords() {
        TopicClassifier custom = new TopicClassifier(new PoliticsRules(
                EnumSet.allOf(Group.class), Sensitivity.NORMAL, "реновация, митинг жильцов", "киберспорт"));
        PoliticsResult blocked = custom.classify("Реновация в Москве продолжится", "", null, Category.WORLD).politics;
        assertTrue(blocked.political);
        assertTrue(blocked.matches.contains("реновация"));

        PoliticsResult allowed = custom.classify("Депутаты обсудили развитие киберспорта", "", null, Category.GAMES).politics;
        assertFalse(allowed.political);
        assertEquals("киберспорт", allowed.exception);
    }

    @Test
    public void bodyMentionCountsLessThanHeadline() {
        PoliticsResult body = classify("Новый стадион откроют весной",
                "Об этом сообщили в правительстве региона.", Category.SPORT).politics;
        assertFalse(body.political);
        assertEquals(1, body.score);
    }
}
