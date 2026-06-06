package io.github.connellite.injector.html;

import io.github.connellite.injector.util.IoUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

public class HtmlInjector {

    private final String html;
    private final DocumentManager documentManager;

    private HtmlInjector(String html) {
        this.html = html;
        this.documentManager = new DocumentManager();
    }

    public static HtmlInjector parse(String html) {
        return new HtmlInjector(html);
    }

    /**
     * Injects CSS into {@code <head>}.
     */
    public HtmlInjector injectCss(String css) {
        if (IoUtils.isBlank(css)) {
            return this;
        }

        Element head = documentManager.getDocument().head();
        head.appendElement("style").attr("type", "text/css").appendText(css);
        return this;
    }

    /**
     * Injects JavaScript at the end of {@code <body>}.
     */
    public HtmlInjector injectJs(String js) {
        if (IoUtils.isBlank(js)) {
            return this;
        }

        Element body = documentManager.getDocument().body();
        body.appendElement("script").attr("type", "text/javascript").appendText(js);
        return this;
    }

    /**
     * Injects an HTML fragment before the closing {@code </body>} tag.
     */
    public HtmlInjector injectHtmlFragment(String fragment) {
        if (IoUtils.isBlank(fragment)) {
            return this;
        }

        documentManager.getDocument().body().append(fragment);
        return this;
    }

    public String toHtml() {
        return documentManager.isParsed()
                ? documentManager.getDocument().outerHtml()
                : html;
    }

    @Override
    public String toString() {
        return toHtml();
    }

    private class DocumentManager {
        private Document parsedDocument;
        private boolean isParsed = false;

        Document getDocument() {
            if (!isParsed) {
                parsedDocument = Jsoup.parse(html);
                isParsed = true;
            }
            return parsedDocument;
        }

        boolean isParsed() {
            return isParsed;
        }
    }
}
