package org.folio.notes.config.properties;

import java.util.Map;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@Getter @Setter
@ConfigurationProperties(prefix = "folio.notes.content.allowed")
public class SafelistProperties {

  String[] tags;
  Map<String, String[]> attributes;
}
