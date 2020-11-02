package org.folio.note;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;

import org.folio.spring.config.TestConfig;

@RunWith(SpringRunner.class)
@ContextConfiguration(classes = TestConfig.class)
public class HtmlSanitizerTest {

  @Autowired
  private HtmlSanitizer sanitizer;

  @Test
  public void shouldNotSanitizeSupportedTags() {
    String content = "<h1>a</h1><h2>b</h2><h3>c</h3><p>d</p><p><strong>e</strong></p><p><em>f</em></p><p><u>g</u></p><p><a href=\"https://examle.com\" rel=\"noopener noreferrer\" target=\"_blank\">h</a></p><ol><li>i</li></ol><ul><li>j</li></ul>";
    String actual = sanitizer.sanitize(content);

    assertEquals(content, actual);
  }

  @Test
  public void shouldNotSanitizeSpacesAndNewLines() {
    String content = "<p>a               b\n\n\n\nc</p>";
    String actual = sanitizer.sanitize(content);

    assertEquals(content, actual);
  }

  @Test
  public void shouldNotSanitizeBlank() {
    String content = null;
    String actual = sanitizer.sanitize(content);

    assertEquals(content, actual);
  }

  @Test
  public void shouldSanitizeUnsupportedTags() {
    String content = "<script></script><img/><br><iframe></iframe>";
    String actual = sanitizer.sanitize(content);

    assertEquals("<br>", actual);
  }
}
