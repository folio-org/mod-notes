package org.folio.note;

import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.safety.Whitelist;
import org.springframework.stereotype.Component;

@Component
public class HtmlSanitizer implements Sanitizer {

  private final Whitelist whitelist;
  private final Document.OutputSettings defaultOutputSettings;

  public HtmlSanitizer(Whitelist whitelist) {
    this.whitelist = whitelist;
    this.defaultOutputSettings = new Document.OutputSettings().prettyPrint(false);
  }

  @Override
  public String sanitize(String content) {
    return StringUtils.isNotBlank(content)
      ? Jsoup.clean(content, "", whitelist, defaultOutputSettings)
      : content;
  }
}
