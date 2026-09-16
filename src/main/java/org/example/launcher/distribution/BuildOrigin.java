package org.example.launcher.distribution;

/**
 * Where a build came from.
 *
 * <ul>
 *   <li>{@code LOCAL} — a build the player saved from their own
 *       instance's mods/configs via the "Save Build" dialog.</li>
 *   <li>{@code SERVER} — a build published by the administrator,
 *       downloaded through the launcher server and installed with a
 *       build.json manifest (see {@link RemoteBuildService}).</li>
 * </ul>
 *
 * Both kinds live side by side in the same {@code builds} folder of an
 * instance, remain fully independent and can be selected, applied and
 * switched between freely.
 */
public enum BuildOrigin {
    LOCAL, SERVER
}
