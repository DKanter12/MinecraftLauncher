package org.example.launcher.service;

import java.io.IOException;

import org.example.launcher.model.AssetIndex;
import org.example.launcher.model.AssetIndexContent;

/**
 * Fetches and parses the asset index for a Minecraft version.
 * <p>
 * The asset index is a JSON document referenced by
 * {@link AssetIndex#url()} that maps every individual asset
 * (textures, sounds, lang files…) to its SHA1 hash and size.
 */
public interface AssetIndexService {

    /**
     * Fetches and parses the asset index content.
     *
     * @param assetIndex the asset index reference from version metadata
     * @return parsed content containing all asset objects
     * @throws IOException if the index could not be fetched or parsed
     */
    AssetIndexContent fetchIndex(AssetIndex assetIndex) throws IOException;
}
