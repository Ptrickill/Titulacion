package ec.rehabelite.auth.application;

/**
 * Names of the claims of the access token. The subject ({@code sub}) is the user id.
 */
public final class JwtClaims {

    public static final String ISSUER = "rehab-elite";
    public static final String EMAIL = "email";
    public static final String FULL_NAME = "fullName";
    public static final String ROLE = "role";
    /**
     * Session version of the account when the token was issued (MEJ-01). A password reset increments it and the
     * older tokens are rejected; a token without the claim counts as version 0.
     */
    public static final String SESSION_VERSION = "sv";

    private JwtClaims() {
    }

}
