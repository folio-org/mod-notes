package org.folio.notes.client;

import java.util.List;

import feign.codec.ErrorDecoder;
import feign.error.AnnotationErrorDecoder;
import feign.error.ErrorCodes;
import feign.error.ErrorHandling;
import lombok.Value;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import org.folio.notes.exception.FolioExternalException;
import org.folio.notes.exception.FolioUnauthorizedException;

@ErrorHandling(codeSpecific =
  {
    @ErrorCodes(codes = {401, 403}, generate = FolioUnauthorizedException.class)
  },
  defaultException = FolioExternalException.class
)
@FeignClient(name = "configurations/entries", configuration = ConfigurationClient.ConfigurationClientConfig.class)
public interface ConfigurationClient {

  @ErrorHandling
  @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
  ConfigurationEntryCollection getConfiguration(@RequestParam("query") String query);

  @Value
  class ConfigurationEntry {

    String id;
    String module;
    String configName;
    String value;
  }

  @Value
  class ConfigurationEntryCollection {

    List<ConfigurationEntry> configs;
    Integer totalRecords;
  }

  @Configuration
  class ConfigurationClientConfig {

    @Bean
    public ErrorDecoder configurationClientErrorDecoder() {
      return AnnotationErrorDecoder.builderFor(ConfigurationClient.class).build();
    }
  }
}
