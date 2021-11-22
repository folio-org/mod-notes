package org.folio.notes.config.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@Getter @Setter
@ConfigurationProperties(prefix = "folio.notes.types")
public class NoteTypesProperties {

  private DefaultOptions defaults;

  @Getter @Setter
  public static class DefaultOptions {

    private String name;

    private String limit;
  }
}
