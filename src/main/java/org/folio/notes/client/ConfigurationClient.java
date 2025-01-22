package org.folio.notes.client;

import java.util.List;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "configurations/entries")
public interface ConfigurationClient {

  @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
  ConfigurationEntryCollection getConfiguration(@RequestParam("query") String query);

  record ConfigurationEntry(String id, String module, String configName, String value) { }

  record ConfigurationEntryCollection(List<ConfigurationEntry> configs, Integer totalRecords) { }

}
