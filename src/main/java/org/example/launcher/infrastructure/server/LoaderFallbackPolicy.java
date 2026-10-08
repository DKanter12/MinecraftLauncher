package org.example.launcher.infrastructure.server;

import java.util.List;
import java.util.Optional;

import org.example.launcher.domain.model.ModLoaderVersion;

/**
 * Перебирает версии загрузчика от новейшей к старейшей, когда игра
 * падает из-за несоответствия загрузчика/модов: подготовитель начинает с
 * новейшей, и каждое подтверждённое несоответствие делает шаг на одну версию назад,
 * пока моды не примут первую подходящую.
 */
public final class LoaderFallbackPolicy {

    private LoaderFallbackPolicy() {
    }

    /**
     * @param newestFirst      доступные версии загрузчика, сначала новейшие
     * @param failedLoaderVersion версия загрузчика, на которой только что произошёл краш
     * @return следующая более старая версия для пробы, или пусто, если
     *         список исчерпан
     */
    public static Optional<ModLoaderVersion> nextOlder(
            List<ModLoaderVersion> newestFirst, String failedLoaderVersion) {
        if (newestFirst == null || newestFirst.isEmpty()
                || failedLoaderVersion == null) {
            return Optional.empty();
        }
        for (int i = 0; i < newestFirst.size(); i++) {
            if (failedLoaderVersion.equals(
                    newestFirst.get(i).loaderVersion())) {
                return i + 1 < newestFirst.size()
                        ? Optional.of(newestFirst.get(i + 1))
                        : Optional.empty();
            }
        }
        return Optional.empty();
    }
}
