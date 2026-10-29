package com.abzed.template.auth;

import com.abzed.template.user.User;

/** Minimal authenticated-user view embedded in the session response. */
public record AuthUser(String usrId, String usrEmail, String usrFullName, String usrMobileNumber, String usrRoleCode) {

    public static AuthUser from(User user) {
        return new AuthUser(
                user.getUsrId().toString(),
                user.getUsrEmail(),
                user.getUsrFullName(),
                user.getUsrMobileNumber(),
                user.getUsrRole().name()
        );
    }
}
