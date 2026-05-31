package handler;

import com.google.gson.JsonElement;

public final class IdUtil {

    private IdUtil() {}

    // Extract a string id from a JSON value that may be a plain string,
    // an extended-JSON ObjectId ({"$oid": "..."}), or null.
    public static String asId(JsonElement el) {
        if (el == null || el.isJsonNull()) return null;
        if (el.isJsonPrimitive()) return el.getAsString();
        if (el.isJsonObject() && el.getAsJsonObject().has("$oid")) {
            return el.getAsJsonObject().get("$oid").getAsString();
        }
        return null;
    }
}
