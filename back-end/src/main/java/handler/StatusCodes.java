package handler;

public class StatusCodes {

    public static final String UNAUTHORIZED = "401 Unauthorized";
    public static final String OK = "200 OK";
    public static final String SERVER_ERROR = "500 Internal Server Error";
    public static final String BAD_REQUEST = "400 Bad Request";
    public static final String NOT_FOUND = "404 Not Found";
    public static final String FORBIDDEN = "403 Forbidden";
    public static final String METHOD_NOT_ALLOWED = "405 Method Not Allowed";
    public static final String CONFLICT = "409 Conflict";
    // Alias for INTERNAL_SERVER_ERROR used previously
    public static final String INTERNAL_SERVER_ERROR = SERVER_ERROR;
    public static final String CREATED = "201 Created";

    /** Map a numeric HTTP status to the "<code> <reason>" string used by handlers. */
    public static String forCode(int code) {
        return switch (code) {
            case 200 -> OK;
            case 201 -> CREATED;
            case 400 -> BAD_REQUEST;
            case 401 -> UNAUTHORIZED;
            case 403 -> FORBIDDEN;
            case 404 -> NOT_FOUND;
            case 405 -> METHOD_NOT_ALLOWED;
            case 409 -> CONFLICT;
            default -> SERVER_ERROR;
        };
    }
}
