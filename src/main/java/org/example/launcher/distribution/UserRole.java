package org.example.launcher.distribution;

/**
 * Role of an account on the launcher server. The role arrives with
 * every authorized session and gates what the launcher unlocks:
 * <ul>
 *   <li>{@code USER} — a regular player: can browse and install the
 *       builds the administrator published, but never sees
 *       administrative functions.</li>
 *   <li>{@code ADMIN} — the administrator: additionally can create,
 *       upload, publish and remove builds (see
 *       {@link api.AdminLauncherServerApi}).</li>
 * </ul>
 * A regular user never gains administrative access just by using the
 * same launcher — the server decides the role, the launcher only
 * follows it.
 */
public enum UserRole {
    USER, ADMIN
}
