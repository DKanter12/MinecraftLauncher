package org.example.launcher.domain.model;

/**
 * Описывает один объект ресурсов внутри индекса ресурсов.
 * <p>
 * Ресурсы адресуются по SHA1-хэшу. Первые два символа
 * хэша образуют подкаталог как в удалённом URL, так и в
 * локальном пути хранения.
 */
public final class AssetObject {

    final String hash;
    final long size;

    public AssetObject(String hash, long size) {
        this.hash = hash;
        this.size = size;
    }

    public String hash() {
        return hash;
    }

    public long size() {
        return size;
    }

    /**
     * Двухсимвольный префикс для разбиения по каталогам, например
     * {@code "ab"} для хэша {@code "abcdef..."}.
     */
    public String hashPrefix() {
        return hash != null && hash.length() >= 2 ? hash.substring(0, 2) : "00";
    }

    @Override
    public String toString() {
        return "AssetObject{hash='" + hash + "', size=" + size + '}';
    }
}
