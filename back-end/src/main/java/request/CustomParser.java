package request;

public class CustomParser {

    // extract java useable values from a raw http request string
    // https://developer.mozilla.org/en-US/docs/Web/HTTP/Messages
    public static ParsedRequest parse(String request) {
        String[] lines = request.split("(\r\n|\r|\n)");
        String requestLine = lines[0];
        String[] requestParts = requestLine.split(" ");
        var result = new ParsedRequest();
        result.setMethod(requestParts[0]);

        var parts = requestParts[1].split("\\?");
        result.setPath(parts[0]);

        if (parts.length == 2) {
            String[] queryParts = parts[1].split("&");
            for (String queryPart : queryParts) {
                if (queryPart.isEmpty()) {
                    continue;
                }
                String[] pair = queryPart.split("=", 2); // tolerate keys with no '='
                result.setQueryParam(pair[0], pair.length > 1 ? pair[1] : "");
            }
        }

        String body = "";
        boolean emptyLine = false;
        for (String line : lines) {
            if (line.contains(":") && !emptyLine) {
                String[] headerParts = line.split(":", 2); // only split on the first ':'
                String key = headerParts[0].trim();
                String value = headerParts[1].trim();
                // setHeaderValue also parses the Cookie header into the cookie map.
                result.setHeaderValue(key, value);
            }
            if (line.equals("")) {
                emptyLine = true;
            }
            if (emptyLine) {
                body += line;
            }
        }
        result.setBody(body);
        return result;
    }
}
