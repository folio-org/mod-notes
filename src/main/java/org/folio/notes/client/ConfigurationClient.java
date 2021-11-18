package org.folio.notes.client;

import java.util.List;

import lombok.Value;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "configurations/entries")
public interface ConfigurationClient {

  @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
  ConfigurationCollection getConfiguration(@RequestParam("query") String query);

  @Value
  class Configuration {
    String id;
    String module;
    String configName;
    String value;
  }

  @Value
  class ConfigurationCollection {
    List<Configuration> configs;
    Integer totalRecords;
  }
}
