package com.bjworld21.congress.publicsite;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;

/** Published message codes are stable; translations live in UTF-8 resource bundles. */
public final class PublicApiMessages {
    private static final ResourceBundle ENGLISH = ResourceBundle.getBundle("public-api", Locale.ENGLISH);
    private static final Map<String, String> CODES = new HashMap<>();
    static {
        for (String code : ENGLISH.keySet()) CODES.put(ENGLISH.getString(code), code);
        ResourceBundle korean = ResourceBundle.getBundle("public-api", Locale.KOREAN);
        for (String code : korean.keySet()) CODES.put(korean.getString(code), code);
    }
    private PublicApiMessages() {}

    public static boolean hasCode(String code) { return ENGLISH.containsKey(code); }

    public static String code(String source, int status) {
        if (source != null && CODES.containsKey(source)) return CODES.get(source);
        if (source != null && source.startsWith("The code is incorrect.")) return "EMAIL_CODE_INCORRECT";
        if (source != null && source.endsWith(" is outside its application period.")) return "REGISTRATION_OPTION_CLOSED";
        if (source != null && source.endsWith(" exceeds the maximum quantity per person.")) return "REGISTRATION_OPTION_QUANTITY";
        if (source != null && source.endsWith(" has insufficient remaining capacity.")) return "REGISTRATION_OPTION_CAPACITY";
        if (source != null && source.endsWith(" must contain English characters only.")) return "ENGLISH_INPUT_REQUIRED";
        if (source != null && (source.endsWith(" characters or less.") || source.endsWith(" is too long"))) return "INPUT_TOO_LONG";
        return switch (status) {
            case 401 -> "LOGIN_REQUIRED";
            case 403 -> "ACCESS_DENIED";
            case 404 -> "NOT_FOUND";
            case 409 -> "REQUEST_CONFLICT";
            case 429 -> "TOO_MANY_ATTEMPTS";
            default -> status >= 500 ? "SERVICE_UNAVAILABLE" : status >= 400 ? "INVALID_REQUEST" : "SUCCESS";
        };
    }

    public static String message(String code, String language) {
        ResourceBundle bundle = ResourceBundle.getBundle("public-api", Locale.forLanguageTag(language),
                ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_DEFAULT));
        return bundle.containsKey(code) ? bundle.getString(code) : ENGLISH.getString("INVALID_REQUEST");
    }

    public static String translate(String source, String language, int status) {
        String code = code(source, status);
        // Preserve specific English validation text until a dedicated translation is registered.
        if (!CODES.containsKey(source) && "en".equals(language) && source != null && !source.isBlank()
                && !source.matches(".*[가-힣].*") && status < 500) return source;
        return message(code, language);
    }
}
