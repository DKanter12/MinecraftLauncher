package org.example.launcher.application.version;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.example.launcher.domain.model.MinecraftVersion;

/**
 * Сортировка списков версий перед передачей интерфейсу.
 * Возвращает новые списки, входные не меняет.
 */
public final class MinecraftVersionSorter {

    private static final Pattern NUMBERS = Pattern.compile("\\d+");

    private MinecraftVersionSorter() {
    }

    /**
     * От новых к старым по дате выхода; версии без даты — в конце.
     */
    public static List<MinecraftVersion> sortByReleaseDate(
            List<MinecraftVersion> versions) {
        List<MinecraftVersion> sorted = new ArrayList<>(versions);
        sorted.sort(Comparator
                .comparing((MinecraftVersion v) -> v.releaseTime().orElse(null),
                        Comparator.nullsLast(Comparator.reverseOrder())));
        return sorted;
    }

    /**
     * По номеру Minecraft от новых к старым ({@code 1.21.11} старше
     * {@code 1.21.10}; снапшоты вида {@code 25w14a} сравниваются
     * по числовым частям).
     */
    public static List<MinecraftVersion> sortByVersion(
            List<MinecraftVersion> versions) {
        List<MinecraftVersion> sorted = new ArrayList<>(versions);
        sorted.sort((a, b) -> compareIds(b.id(), a.id()));
        return sorted;
    }

    static int compareIds(String a, String b) {
        List<Integer> na = numbers(a);
        List<Integer> nb = numbers(b);
        int length = Math.max(na.size(), nb.size());
        for (int i = 0; i < length; i++) {
            int x = i < na.size() ? na.get(i) : -1;
            int y = i < nb.size() ? nb.get(i) : -1;
            if (x != y) {
                return Integer.compare(x, y);
            }
        }
        return a.compareTo(b);
    }

    private static List<Integer> numbers(String id) {
        List<Integer> result = new ArrayList<>();
        if (id == null) {
            return result;
        }
        Matcher matcher = NUMBERS.matcher(id);
        while (matcher.find()) {
            try {
                result.add(Integer.parseInt(matcher.group()));
            } catch (NumberFormatException ignored) {
                // пропускаем слишком длинные числовые куски
            }
        }
        return result;
    }
}
