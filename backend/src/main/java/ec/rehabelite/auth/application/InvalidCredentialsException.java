package ec.rehabelite.auth.application;

/**
 * Login rejected. The same exception (and the same user-facing message) is used for an unknown email,
 * a wrong password, a locked account and an inactive account, so the response never reveals which one failed.
 */
public class InvalidCredentialsException extends RuntimeException {

    public static final String USER_MESSAGE =
            "Correo o contraseña incorrectos, o la cuenta está bloqueada temporalmente.";

    public InvalidCredentialsException() {
        super(USER_MESSAGE);
    }

}
