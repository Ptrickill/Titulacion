package ec.rehabelite.auth.application;

/**
 * Names of the claims of the access token. The subject ({@code sub}) is the user id.
 */
public final class JwtClaims {

    public static final String ISSUER = "rehab-elite";
    public static final String EMAIL = "email";
    public static final String FULL_NAME = "fullName";
    public static final String ROLE = "role";

    private JwtClaims() {
    }

}
