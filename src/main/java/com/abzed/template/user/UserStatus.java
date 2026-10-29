package com.abzed.template.user;

/**
 * Lifecycle state of a user account.
 * <ul>
 *   <li>{@code DRAFT}      — registered but not yet verified; re-registration updates the draft.</li>
 *   <li>{@code ACTIVE}     — verified and usable.</li>
 *   <li>{@code INACTIVE}   — temporarily disabled.</li>
 *   <li>{@code DEACTIVATED} — permanently closed.</li>
 * </ul>
 */
public enum UserStatus {
    DRAFT,
    ACTIVE,
    INACTIVE,
    DEACTIVATED
}
