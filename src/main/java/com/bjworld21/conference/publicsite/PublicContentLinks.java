package com.bjworld21.conference.publicsite;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;

/** Rebase sanitized, administrator-authored internal links without evaluating template expressions. */
public final class PublicContentLinks {
    private PublicContentLinks() { }

    public static String render(String html, PublicSiteContext site) {
        if (html == null || html.isBlank()) return "";
        var document = Jsoup.parseBodyFragment(html);
        document.outputSettings().prettyPrint(false);
        for (Element anchor : document.select("a[href]")) {
            String href = anchor.attr("href");
            if (!href.startsWith("/admin") && !href.startsWith("/api/admin")) anchor.attr("href", site.pageUrl(href));
        }
        for (Element image : document.select("img[src]")) {
            String src = image.attr("src");
            if (src.startsWith("/api/sponsors/") || src.startsWith("/api/popups/") || src.startsWith("/public/speakers/")) {
                image.attr("src", site.apiUrl(src));
            }
        }
        return document.body().html();
    }
}
