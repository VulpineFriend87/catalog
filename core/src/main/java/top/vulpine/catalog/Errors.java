package top.vulpine.catalog;

/**
 * Finds the message of an exception's root cause.
 */
public final class Errors {

    private Errors() {}

    /**
     * The message of the real cause inside a failed future.
     *
     * @param error what was thrown
     * @return the message, or the class name when there is none
     */
    public static String rootMessage(Throwable error) {

        Throwable cause = error;

        while (cause.getCause() != null && cause.getMessage() == null) {
            cause = cause.getCause();
        }

        return cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
    }

}
