package org.folio.notes.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.folio.notes.client.ConfigurationClient;
import org.folio.notes.client.ConfigurationClient.ConfigurationEntry;
import org.folio.notes.service.ConfigurationService;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConfigurationServiceImpl implements ConfigurationService {

  private static final String MODULE_NAME = "NOTES";
  private static final String CONFIG_QUERY = "module==%s and configName==%s";

  private final ConfigurationClient client;

  @Override
  public String getConfigValue(String configName, String defaultValue) {
    log.debug("getConfigValue:: trying to get configValue by configName: {}", configName);
    try {
      var configurations = client.getConfiguration(String.format(CONFIG_QUERY, MODULE_NAME, configName));

      if (configurations != null && configurations.totalRecords() > 0) {
        ConfigurationEntry configuration = configurations.configs().getFirst();
        log.info("getConfigValue:: configValue loaded by configName: {}", configName);
        return configuration.value();
      }
    } catch (Exception ex) {
      log.warn("Failed to get configuration={} : {}", configName, ex.getMessage());
    }
    log.warn("getConfigValue:: error loading configValue with configName: {}, returning defaultValue: {}",
      configName, defaultValue);
    return defaultValue;
  }
}
