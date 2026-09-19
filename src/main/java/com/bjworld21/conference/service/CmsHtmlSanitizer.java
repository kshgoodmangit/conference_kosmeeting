package com.bjworld21.conference.service;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class CmsHtmlSanitizer {
    private static final String SAFE_STYLE_ATTRIBUTE = "data-cms-safe-style";
    private static final Pattern HEX_COLOR = Pattern.compile("#[0-9a-fA-F]{3}([0-9a-fA-F]{3})?");
    private static final Pattern FONT_FAMILY = Pattern.compile("[a-zA-Z0-9가-힣 ,.'\"_-]{1,120}");
    private static final Pattern PIXEL_SIZE = Pattern.compile("([1-9][0-9]{0,3})px");
    private static final Pattern PERCENTAGE_SIZE = Pattern.compile("(100|[1-9][0-9]?)%");
    private static final Pattern YOUTUBE_EMBED_PATH = Pattern.compile("^/embed/([A-Za-z0-9_-]{11})/?$");
    private static final Set<Integer> ALLOWED_FONT_SIZES = Set.of(10, 12, 14, 16, 18, 20, 24, 28, 32, 36, 48);
    private static final Set<String> YOUTUBE_HOSTS = Set.of(
            "youtube.com",
            "www.youtube.com",
            "youtube-nocookie.com",
            "www.youtube-nocookie.com"
    );
    private static final Safelist CMS_CONTENT_SAFELIST = Safelist.relaxed()
            .addTags("article", "section", "figure", "figcaption", "hr", "iframe", "colgroup", "col")
            .addAttributes(":all", "class", SAFE_STYLE_ATTRIBUTE)
            .addAttributes("a", "target", "title")
            .addAttributes("img", "loading", "width", "height", "align", "alt", "title")
            .addAttributes("iframe", "src", "width", "height", "title", "frameborder", "loading",
                    "referrerpolicy", "allow", "allowfullscreen")
            .addAttributes("table", "cellpadding", "cellspacing", "border", "width", "height", "align")
            .addAttributes("colgroup", "span", "width")
            .addAttributes("col", "span", "width")
            .addAttributes("td", "colspan", "rowspan", "width", "height", "align", "valign")
            .addAttributes("th", "colspan", "rowspan", "width", "height", "align", "valign")
            .removeAttributes("a", "rel")
            .removeProtocols("a", "href", "ftp")
            .addProtocols("a", "href", "http", "https", "mailto", "tel")
            .addProtocols("img", "src", "http", "https")
            .preserveRelativeLinks(true)
            .addProtocols("iframe", "src", "https");

    public String sanitize(String html) {
        if (html == null || html.isBlank()) {
            return "";
        }

        Document source = Jsoup.parseBodyFragment(html);
        sanitizeYoutubeIframes(source);
        for (Element element : source.select("[" + SAFE_STYLE_ATTRIBUTE + "]")) {
            element.removeAttr(SAFE_STYLE_ATTRIBUTE);
        }
        for (Element element : source.select("[style]")) {
            String safeStyle = sanitizeStyle(element);
            if (!safeStyle.isBlank()) {
                element.attr(SAFE_STYLE_ATTRIBUTE, safeStyle);
            }
        }

        String cleaned = Jsoup.clean(source.body().html(), "https://localhost/", CMS_CONTENT_SAFELIST);
        Document sanitized = Jsoup.parseBodyFragment(cleaned);
        for (Element element : sanitized.select("[" + SAFE_STYLE_ATTRIBUTE + "]")) {
            element.attr("style", element.attr(SAFE_STYLE_ATTRIBUTE));
            element.removeAttr(SAFE_STYLE_ATTRIBUTE);
        }
        sanitizeLinkTargets(sanitized);
        return sanitized.body().html();
    }

    public String summarize(String html, String fallback) {
        String text = html == null ? "" : Jsoup.parse(html).text().trim();
        if (text.isEmpty()) {
            return fallback;
        }
        return text.length() <= 160 ? text : text.substring(0, 157) + "...";
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
            case "color", "background-color" -> HEX_COLOR.matcher(value).matches()
                    ? value.toUpperCase(Locale.ROOT) : null;
            case "font-size" -> allowedFontSize(value);
            case "font-family" -> FONT_FAMILY.matcher(value).matches() ? value : null;
            case "float" -> "img".equals(element.tagName()) && isOneOf(value, "left", "right", "none")
                    ? value.toLowerCase(Locale.ROOT) : null;
            case "width" -> "img".equals(element.tagName())
                    ? allowedImageDimension(value)
                    : "col".equals(element.tagName()) ? allowedColumnDimension(value) : null;
            case "height" -> "img".equals(element.tagName()) ? allowedImageDimension(value) : null;
            case "margin", "margin-left", "margin-right" -> "img".equals(element.tagName())
                    ? allowedImageMargin(value) : null;
            default -> null;
        };
    }

    private String allowedFontSize(String value) {
        Matcher matcher = PIXEL_SIZE.matcher(value.toLowerCase(Locale.ROOT));
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
        Matcher matcher = PIXEL_SIZE.matcher(normalized);
        if (!matcher.matches()) {
            return null;
        }
        int pixels = Integer.parseInt(matcher.group(1));
        return pixels <= 2000 ? pixels + "px" : null;
    }

    private String allowedColumnDimension(String value) {
        String normalized = value.toLowerCase(Locale.ROOT);
        Matcher pixelMatcher = PIXEL_SIZE.matcher(normalized);
        if (pixelMatcher.matches()) {
            int pixels = Integer.parseInt(pixelMatcher.group(1));
            return pixels <= 2000 ? pixels + "px" : null;
        }
        return PERCENTAGE_SIZE.matcher(normalized).matches() ? normalized : null;
    }

    private String allowedImageMargin(String value) {
        String normalized = value.toLowerCase(Locale.ROOT);
        if ("0".equals(normalized)) {
            return normalized;
        }
        Matcher matcher = PIXEL_SIZE.matcher(normalized);
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

    private void sanitizeYoutubeIframes(Document document) {
        for (Element iframe : document.select("iframe")) {
            String safeUrl = safeYoutubeEmbedUrl(iframe.attr("src"));
            if (safeUrl == null) {
                iframe.remove();
                continue;
            }

            iframe.clearAttributes();
            iframe.attr("src", safeUrl);
            iframe.attr("width", "640");
            iframe.attr("height", "360");
            iframe.attr("title", "YouTube 영상");
            iframe.attr("frameborder", "0");
            iframe.attr("loading", "lazy");
            iframe.attr("referrerpolicy", "strict-origin-when-cross-origin");
            iframe.attr("allow", "encrypted-media; picture-in-picture");
            iframe.attr("allowfullscreen", "");
        }
    }

    private String safeYoutubeEmbedUrl(String value) {
        try {
            URI uri = new URI(value == null ? "" : value.trim());
            String host = uri.getHost();
            if (!"https".equalsIgnoreCase(uri.getScheme())
                    || host == null
                    || !YOUTUBE_HOSTS.contains(host.toLowerCase(Locale.ROOT))
                    || uri.getUserInfo() != null
                    || uri.getPort() != -1
                    || uri.getQuery() != null
                    || uri.getFragment() != null) {
                return null;
            }

            Matcher matcher = YOUTUBE_EMBED_PATH.matcher(uri.getPath());
            return matcher.matches()
                    ? "https://www.youtube-nocookie.com/embed/" + matcher.group(1)
                    : null;
        } catch (URISyntaxException exception) {
            return null;
        }
    }
}
