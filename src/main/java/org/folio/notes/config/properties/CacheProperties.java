package org.folio.notes.config.properties;

import java.util.Map;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@Getter @Setter
@ConfigurationProperties(prefix = "folio.notes.cache")
public class CacheProperties {

  private Map<String, CacheOptions> configs;

  @Getter @Setter
  public static class CacheOptions {

    int initialCapacity;
    int maximumSize;
    int expireAfterWrite;
  }
}
