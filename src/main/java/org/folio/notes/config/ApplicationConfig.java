package org.folio.notes.config;

import org.folio.notes.config.properties.SafelistProperties;
import org.jsoup.safety.Safelist;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableCaching
public class ApplicationConfig {

  @Bean
  public Safelist safelist(SafelistProperties safelistProperties) {
    Safelist safelist = new Safelist().addTags(safelistProperties.getTags());
    safelistProperties.getAttributes().forEach((tag, attrs) -> {
      // To make an attribute valid for all tags, use the pseudo tag :all, e.g. addAttributes(":all", "class").
      if (tag.equals("all")) {
        tag = ":" + tag;
      }
      safelist.addAttributes(tag, attrs);
    });
    return safelist;
  }
}
