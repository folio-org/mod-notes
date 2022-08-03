package org.folio.notes.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import java.util.concurrent.TimeUnit;
import org.folio.notes.config.properties.CacheProperties;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CacheConfig {

  public static final String CACHE_USERS_BY_ID = "users-by-id";

  @Bean
  public Caffeine<Object, Object> usersByIdCacheConfig(CacheProperties cacheProperties) {
    var cacheOptions = cacheProperties.getConfigs().get(CACHE_USERS_BY_ID);
    return Caffeine.newBuilder()
      .initialCapacity(cacheOptions.getInitialCapacity())
      .maximumSize(cacheOptions.getMaximumSize())
      .expireAfterWrite(cacheOptions.getExpireAfterWrite(), TimeUnit.MINUTES);
  }

  @Bean
  public CacheManager cacheManager(Caffeine<Object, Object> usersByIdCacheConfig) {
    CaffeineCacheManager cacheManager = new CaffeineCacheManager(CACHE_USERS_BY_ID);
    cacheManager.setCaffeine(usersByIdCacheConfig);
    return cacheManager;
  }
}
