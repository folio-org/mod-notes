package org.folio.notes.util;

import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Component;

@Component
public class HtmlSanitizer implements Sanitizer {

  private final Safelist safelist;
  private final Document.OutputSettings defaultOutputSettings;

  public HtmlSanitizer(Safelist safelist) {
    this.safelist = safelist;
    this.defaultOutputSettings = new Document.OutputSettings().prettyPrint(false);
  }

  @Override
  public String sanitize(String content) {
    return StringUtils.isNotBlank(content)
      ? Jsoup.clean(content, "", safelist, defaultOutputSettings)
      : content;
  }
}
