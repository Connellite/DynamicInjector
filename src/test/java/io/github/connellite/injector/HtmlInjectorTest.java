package io.github.connellite.injector;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HtmlInjectorTest {

    @Test
    void shouldInjectCssIntoHead() {
        String html = "<html><head></head><body></body></html>";
        String css = "body { color: red; }";

        String result = HtmlInjector.parse(html)
                .injectCss(css)
                .toHtml();

        Document doc = Jsoup.parse(result);
        assertEquals(1, doc.select("head style").size());
        assertTrue(doc.select("head style").first().html().contains(css));
    }

    @Test
    void shouldNotInjectEmptyCss() {
        String html = "<html><head></head><body></body></html>";
        String original = HtmlInjector.parse(html).toHtml();

        String result = HtmlInjector.parse(html)
                .injectCss("")
                .injectCss(null)
                .toHtml();

        assertEquals(original, result);
    }

    @Test
    void shouldInjectJsBeforeBodyClose() {
        String html = "<html><head></head><body></body></html>";
        String js = "alert('test');";

        String result = HtmlInjector.parse(html)
                .injectJs(js)
                .toHtml();

        Document doc = Jsoup.parse(result);
        assertEquals(1, doc.select("body script").size());
        assertTrue(doc.select("body script").first().html().contains(js));
    }

    @Test
    void shouldInjectMultipleJsScripts() {
        String html = "<html><head></head><body></body></html>";

        String result = HtmlInjector.parse(html)
                .injectJs("alert('1');")
                .injectJs("alert('2');")
                .toHtml();

        Document doc = Jsoup.parse(result);
        assertEquals(2, doc.select("body script").size());
    }

    @Test
    void shouldInjectHtmlFragment() {
        String html = "<html><head></head><body><div id='content'></div></body></html>";
        String fragment = "<p>New element</p>";

        String result = HtmlInjector.parse(html)
                .injectHtmlFragment(fragment)
                .toHtml();

        Document doc = Jsoup.parse(result);
        assertTrue(doc.select("body").html().contains(fragment));
    }

    @Test
    void shouldHandleHtmlWithoutHead() {
        String html = "<html><body></body></html>";
        String css = "body { color: red; }";

        String result = HtmlInjector.parse(html)
                .injectCss(css)
                .toHtml();

        Document doc = Jsoup.parse(result);
        assertEquals(1, doc.select("head style").size());
    }

    @Test
    void shouldHandleHtmlWithoutBody() {
        String html = "<html><head></head></html>";
        String js = "alert('test');";

        String result = HtmlInjector.parse(html)
                .injectJs(js)
                .toHtml();

        Document doc = Jsoup.parse(result);
        assertEquals(1, doc.select("body script").size());
    }

    @Test
    void shouldChainInjections() {
        String html = "<html><head></head><body></body></html>";
        String css = ".test { color: red; }";
        String js = "console.log('test');";
        String fragment = "<div class='test'></div>";

        String result = HtmlInjector.parse(html)
                .injectCss(css)
                .injectJs(js)
                .injectHtmlFragment(fragment)
                .toHtml();

        Document doc = Jsoup.parse(result);

        assertTrue(doc.select("head style").html().contains(css),
                "CSS was not injected into head");

        assertTrue(doc.select("body script").html().contains(js),
                "JS was not injected into body");

        Elements injectedElements = doc.select("body div.test");
        assertFalse(injectedElements.isEmpty(),
                "HTML fragment was not injected into body");
        assertEquals("test", injectedElements.first().className(),
                "Injected element has unexpected class name");
    }

    @Test
    void toStringShouldReturnSameAsToHtml() {
        String html = "<html><head></head><body></body></html>";
        HtmlInjector injector = HtmlInjector.parse(html);
        assertEquals(injector.toHtml(), injector.toString());
    }
}
