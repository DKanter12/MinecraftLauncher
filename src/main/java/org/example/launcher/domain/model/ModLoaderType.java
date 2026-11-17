package org.example.launcher.domain.model;

/**
 * Определяет поддерживаемый тип игры (инстанса).
 * <p>
 * {@link #VANILLA} — псевдотип загрузчика для единой модели
 * инстансов: инстанс типа VANILLA запускает немодифицированную
 * игру, а остальные типы — мод-загрузчики, требующие совпадающих
 * {@link ModLoaderVersionProvider} и {@link ModLoaderInstaller},
 * зарегистрированных в {@link ModLoaderRegistry}.
 */
public enum ModLoaderType {

    VANILLA("Vanilla"),
    FABRIC("Fabric"),
    FORGE("Forge"),
    NEOFORGE("NeoForge"),
    QUILT("Quilt");

    private final String displayName;

    ModLoaderType(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
