package com.bjworld21.conference.service;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

@Component
public class MailHtmlSanitizer {
    private static final String UNSUBSCRIBE_PLACEHOLDER = "{{unsubscribeUrl}}";
    private static final String SAFE_UNSUBSCRIBE_PLACEHOLDER = "https://unsubscribe.invalid/personalized-link";
    private static final String SAFE_STYLE_ATTRIBUTE = "data-mail-safe-style";
    private static final Pattern HEX_COLOR = Pattern.compile("#[0-9a-fA-F]{3}([0-9a-fA-F]{3})?");
    private static final Pattern FONT_FAMILY = Pattern.compile("[a-zA-Z0-9가-힣 ,.'\"_-]{1,120}");
    private static final Pattern PIXEL_SIZE = Pattern.compile("([1-9][0-9]{0,3})px");
    private static final Set<Integer> ALLOWED_FONT_SIZES = Set.of(10, 12, 14, 16, 18, 20, 24, 28, 32, 36, 48);
    private static final Safelist SAFELIST = Safelist.relaxed()
            .addTags("article", "section", "div", "span", "figure", "figcaption", "table", "thead", "tbody", "tfoot", "tr", "th", "td")
            .addAttributes(":all", SAFE_STYLE_ATTRIBUTE)
            .addAttributes("a", "target", "title")
            .addAttributes("img", "loading", "width", "height", "align", "alt", "title")
            .addAttributes("table", "cellpadding", "cellspacing", "border", "width", "height", "align")
            .addAttributes("td", "colspan", "rowspan", "width", "height", "align", "valign")
            .addAttributes("th", "colspan", "rowspan", "width", "height", "align", "valign")
            .removeAttributes("a", "rel")
            .removeProtocols("a", "href", "ftp")
            .addProtocols("a", "href", "http", "https", "mailto", "tel")
            .addProtocols("img", "src", "http", "https", "cid");

    public String sanitize(String html) {
        if (html == null) {
            return "";
        }

        String safePlaceholderHtml = html.replace(UNSUBSCRIBE_PLACEHOLDER, SAFE_UNSUBSCRIBE_PLACEHOLDER);
        Document source = Jsoup.parseBodyFragment(safePlaceholderHtml);
        for (Element element : source.select("[" + SAFE_STYLE_ATTRIBUTE + "]")) {
            element.removeAttr(SAFE_STYLE_ATTRIBUTE);
        }
        for (Element element : source.select("[style]")) {
            String safeStyle = sanitizeStyle(element);
            if (!safeStyle.isBlank()) {
                element.attr(SAFE_STYLE_ATTRIBUTE, safeStyle);
            }
        }

        String cleaned = Jsoup.clean(source.body().html(), SAFELIST);
        Document sanitized = Jsoup.parseBodyFragment(cleaned);
        for (Element element : sanitized.select("[" + SAFE_STYLE_ATTRIBUTE + "]")) {
            element.attr("style", element.attr(SAFE_STYLE_ATTRIBUTE));
            element.removeAttr(SAFE_STYLE_ATTRIBUTE);
        }
        sanitizeLinkTargets(sanitized);
        return sanitized.body().html().replace(SAFE_UNSUBSCRIBE_PLACEHOLDER, UNSUBSCRIBE_PLACEHOLDER);
    }

    public boolean hasVisibleContent(String sanitizedHtml) {
        return sanitizedHtml != null
                && (!Jsoup.parse(sanitizedHtml).text().isBlank() || sanitizedHtml.contains("<img"));
    }

    private String sanitizeStyle(Element element) {
        List<String> safeDeclarations = new ArrayList<>();
        for (String declaration : element.attr("style").split(";")) {
            String[] parts = declaration.split(":", 2);
            if (parts.length != 2) {
                continue;
            }

            String property = parts[0].trim().toLowerCase(Locale.ROOT);
            String value = parts[1].trim();
            String safeValue = safeCssValue(element, property, value);
            if (safeValue != null) {
                safeDeclarations.add(property + ": " + safeValue);
            }
        }
        return String.join("; ", safeDeclarations);
    }

    private String safeCssValue(Element element, String property, String value) {
        return switch (property) {
            case "text-align" -> isOneOf(value, "left", "center", "right", "justify")
                    ? value.toLowerCase(Locale.ROOT) : null;
            case "color", "background-color" -> HEX_COLOR.matcher(value).matches() ? value.toUpperCase(Locale.ROOT) : null;
            case "font-size" -> allowedFontSize(value);
            case "font-family" -> FONT_FAMILY.matcher(value).matches() ? value : null;
            case "float" -> "img".equals(element.tagName()) && isOneOf(value, "left", "right", "none")
                    ? value.toLowerCase(Locale.ROOT) : null;
            case "width", "height" -> "img".equals(element.tagName()) ? allowedImageDimension(value) : null;
            case "margin", "margin-left", "margin-right" -> "img".equals(element.tagName()) ? allowedImageMargin(value) : null;
            default -> null;
        };
    }

    private String allowedFontSize(String value) {
        java.util.regex.Matcher matcher = PIXEL_SIZE.matcher(value.toLowerCase(Locale.ROOT));
        if (!matcher.matches()) {
            return null;
        }
        int size = Integer.parseInt(matcher.group(1));
        return ALLOWED_FONT_SIZES.contains(size) ? size + "px" : null;
    }

    private String allowedImageDimension(String value) {
        String normalized = value.toLowerCase(Locale.ROOT);
        if ("auto".equals(normalized)) {
            return normalized;
        }
        java.util.regex.Matcher matcher = PIXEL_SIZE.matcher(normalized);
        if (!matcher.matches()) {
            return null;
        }
        int pixels = Integer.parseInt(matcher.group(1));
        return pixels <= 2000 ? pixels + "px" : null;
    }

    private String allowedImageMargin(String value) {
        String normalized = value.toLowerCase(Locale.ROOT);
        if ("0".equals(normalized)) {
            return normalized;
        }
        java.util.regex.Matcher matcher = PIXEL_SIZE.matcher(normalized);
        if (!matcher.matches()) {
            return null;
        }
        int pixels = Integer.parseInt(matcher.group(1));
        return pixels <= 100 ? pixels + "px" : null;
    }

    private boolean isOneOf(String value, String... allowedValues) {
        for (String allowedValue : allowedValues) {
            if (allowedValue.equalsIgnoreCase(value)) {
                return true;
            }
        }
        return false;
    }

    private void sanitizeLinkTargets(Document document) {
        for (Element link : document.select("a[target]")) {
            String target = link.attr("target");
            if ("_blank".equalsIgnoreCase(target)) {
                link.attr("target", "_blank");
                link.attr("rel", "noopener noreferrer");
            } else if ("_self".equalsIgnoreCase(target)) {
                link.attr("target", "_self");
            } else {
                link.removeAttr("target");
            }
        }
    }
}
