package org.folio.notes.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

@UnitTest
class HtmlSanitizerTest {

  private HtmlSanitizer sanitizer;

  @BeforeEach
  void setUp() {
    sanitizer = new HtmlSanitizer();
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = {"", "   "})
  void sanitize_shouldReturnContentUnchanged_whenContentIsBlank(String content) {
    var result = sanitizer.sanitize(content);

    assertEquals(content, result);
  }

  @Test
  void sanitize_shouldReturnPlainText_whenContentHasNoHtml() {
    var result = sanitizer.sanitize("plain text");

    assertEquals("plain text", result);
  }

  @Test
  void sanitize_shouldKeepAllowedTags() {
    var content = "<p>para</p><strong>bold</strong><em>italic</em><u>underline</u>"
      + "<ol><li>one</li></ol><ul><li>two</li></ul><h1>h1</h1><h2>h2</h2><h3>h3</h3>line1<br>line2";

    var result = sanitizer.sanitize(content);

    assertEquals(content, result);
  }

  @Test
  void sanitize_shouldKeepAllowedAttributes_onAllowedTag() {
    var content = "<a href=\"https://example.org\" rel=\"noopener\" target=\"_blank\" "
      + "class=\"link\" style=\"color:red\">click</a>";

    var result = sanitizer.sanitize(content);

    assertEquals(content, result);
  }

  @Test
  void sanitize_shouldStripDisallowedAttributes_fromAllowedTag() {
    var result = sanitizer.sanitize("<p onclick=\"alert(1)\" id=\"x\">text</p>");

    assertEquals("<p>text</p>", result);
  }

  @Test
  void sanitize_shouldStripDisallowedTag_butKeepItsText() {
    var result = sanitizer.sanitize("<div><p>kept</p><span>also kept</span></div>");

    assertEquals("<p>kept</p>also kept", result);
  }

  @Test
  void sanitize_shouldRemoveScriptTagAndItsContent() {
    var result = sanitizer.sanitize("<p>before</p><script>alert('xss')</script><p>after</p>");

    assertEquals("<p>before</p><p>after</p>", result);
  }

  @Test
  void sanitize_shouldRemoveDisallowedVoidTag_entirely() {
    var result = sanitizer.sanitize("<img src=\"x\" onerror=\"alert(1)\">");

    assertEquals("", result);
  }

  @Test
  void sanitize_shouldNotPrettyPrintOutput() {
    var result = sanitizer.sanitize("<ul><li>one</li><li>two</li></ul>");

    assertEquals("<ul><li>one</li><li>two</li></ul>", result);
  }
}
