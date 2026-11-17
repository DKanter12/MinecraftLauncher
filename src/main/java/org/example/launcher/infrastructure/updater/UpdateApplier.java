package org.example.launcher.infrastructure.updater;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Применяет подготовленное обновление, передавая управление небольшому скрипту
 * обновляльщика: Java не может заменить собственные заблокированные jar на Windows, поэтому
 * скрипт ждёт выхода процесса лаунчера, копирует
 * подготовленные файлы поверх домашнего каталога приложения, записывает новый
 * {@code version.txt} и перезапускает лаунчер.
 * <p>
 * Затрагиваются только собственные файлы лаунчера (jar, скрипты, ресурсы из
 * пакета обновления) — файлы Minecraft и данные пользователя
 * живут в другом месте и не затрагиваются.
 */
public final class UpdateApplier {

    /** Имя скрипта обновляльщика внутри каталога обновлений. */
    public static final String UPDATER_NAME = "update.bat";

    private UpdateApplier() {
    }

    /** Всё нужное для передачи управления скрипту обновляльщика. */
    public record ApplyPlan(Path stageDir, Path appHome, String version,
                            List<String> restartCommand) {
    }

    /**
     * Строит план применения для подготовленного обновления, или пусто, если
     * раскладка приложения не распознана (запуск из классов Gradle при разработке,
     * неизвестная упаковка) — тогда пользователь копирует подготовленные
     * файлы вручную.
     */
    public static Optional<ApplyPlan> plan(Path stageDir, String version) {
        Path appHome = AppVersion.appHome();
        if (appHome == null || !Files.isDirectory(stageDir)) {
            return Optional.empty();
        }
        Optional<List<String>> restart = restartCommand(appHome);
        if (restart.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new ApplyPlan(stageDir, appHome, version,
                restart.get()));
    }

    /**
     * Записывает скрипт обновляльщика и возвращает его, очищая все прочие
     * подготовленные обновления. Вызывающая сторона запускает его и сразу выходит.
     */
    public static Path writeUpdater(UpdateService service, ApplyPlan plan)
            throws IOException {
        Path updatesDir = plan.stageDir().getParent();
        StringBuilder startLine = new StringBuilder("start \"\"");
        for (String arg : plan.restartCommand()) {
            startLine.append(' ');
            startLine.append(arg.contains(" ") ? "\"" + arg + "\"" : arg);
        }
        String script = "@echo off\r\n"
                + "rem Minecraft Launcher updater: waits for PID %1, applies staged files, restarts\r\n"
                + ":waitloop\r\n"
                + "tasklist /FI \"PID eq %1\" 2>NUL | find \"%1\" >NUL\r\n"
                + "if %errorlevel%==0 (\r\n"
                + "  timeout /t 1 /nobreak >NUL\r\n"
                + "  goto waitloop\r\n"
                + ")\r\n"
                + "xcopy \"" + plan.stageDir() + "\\*\" \"" + plan.appHome()
                + "\\\" /E /I /Y\r\n"
                + "echo " + plan.version() + "> \"" + plan.appHome()
                + "\\version.txt\"\r\n"
                + "rmdir /S /Q \"" + plan.stageDir() + "\"\r\n"
                + "del \"" + updatesDir.resolve(UpdateService.PENDING_FILE_NAME) + "\"\r\n"
                + startLine + "\r\n"
                + "del \"%~f0\"\r\n";
        Path updater = updatesDir.resolve(UPDATER_NAME);
        Files.writeString(updater, script);
        service.clearStagedExcept(plan.stageDir());
        return updater;
    }

    /**
     * Запускает обновляльщик отдельно и возвращается — вызывающая сторона обязана
     * сразу выйти, чтобы освободить заблокированные файлы.
     */
    public static void launchAndExit(Path updater, long pid) throws IOException {
        new ProcessBuilder("cmd", "/c", "start", "", updater.toString(),
                String.valueOf(pid))
                .directory(updater.getParent().toFile())
                .start();
    }

    private static Optional<List<String>> restartCommand(Path appHome) {
        Path bin = appHome.resolve("bin");
        if (Files.isDirectory(bin)) {
            Path preferred = bin.resolve("MinecraftLauncher.bat");
            if (Files.isRegularFile(preferred)) {
                return Optional.of(List.of(preferred.toString()));
            }
            try (var stream = Files.list(bin)) {
                List<Path> scripts = new ArrayList<>();
                for (Path p : stream.filter(p -> p.getFileName().toString()
                        .endsWith(".bat")).sorted().toList()) {
                    scripts.add(p);
                }
                if (!scripts.isEmpty()) {
                    return Optional.of(
                            List.of(scripts.get(0).toString()));
                }
            } catch (IOException ignored) {
                // откат к поиску jar
            }
        }
        try (var stream = Files.list(appHome)) {
            List<Path> jars = new ArrayList<>();
            for (Path p : stream.filter(p -> p.getFileName().toString()
                    .endsWith(".jar")).sorted().toList()) {
                jars.add(p);
            }
            if (jars.size() == 1) {
                return Optional.of(
                        List.of("java", "-jar", jars.get(0).toString()));
            }
        } catch (IOException ignored) {
            // команда перезапуска отсутствует
        }
        return Optional.empty();
    }
}
