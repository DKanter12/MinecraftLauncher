package org.example.launcher.infrastructure.java;

import java.nio.file.Path;
import java.util.List;

import org.example.launcher.domain.model.JavaRuntime;

/**
 * Сканирует систему в поисках установок Java, доступных в системе хоста.
 * <p>
 * Реализации сканируют переменные окружения, системные пути,
 * платформенные реестры и типовые каталоги установок, строя список
 * доступных рантаймов. Это «сенсорная» сторона разрешения Java
 * только для чтения; собственно логика подбора находится в
 * {@link JavaFinder}.
 * <p>
 * Интерфейс спроектирован для тестируемости — реализации, которые
 * вызывают {@code java -version} или опрашивают реестр Windows, можно
 * заменять заглушками в тестах.
 */
public interface JavaScanner {

    /**
     * Сканирует систему в поисках всех обнаруживаемых рантаймов Java.
     * <p>
     * Возвращаемый список не упорядочен по приоритету;
     * за выбор лучшего соответствия отвечает
     * {@link JavaFinder}.
     *
     * @return список обнаруженных рантаймов (может быть пустым, никогда {@code null})
     */
    List<JavaRuntime> scanInstalledRuntimes();

    /**
     * Проверяет один каталог, который предполагается корнем JDK/JRE
     * (т.е. содержит {@code bin/java} или {@code bin/java.exe}).
     *
     * @param homeDir кандидатный каталог в стиле {@code JAVA_HOME}
     * @param source  источник обнаружения для привязки к результату
     * @return {@link JavaRuntime}, если каталог корректен, или
     *         {@code null}, если исполняемый файл не найден либо версию
     *         определить не удалось
     */
    JavaRuntime detectFromPath(Path homeDir, JavaRuntime.Source source);
}
