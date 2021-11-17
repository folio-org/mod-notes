package org.folio.notes.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import org.folio.notes.client.ConfigurationClient;
import org.folio.notes.client.ConfigurationClient.Configuration;
import org.folio.notes.client.ConfigurationClient.ConfigurationCollection;
import org.folio.notes.service.ConfigurationService;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConfigurationServiceImpl implements ConfigurationService {

  private static final String MODULE_NAME = "mod-notes";
  private static final String CONFIG_QUERY = "module==%s and configName==%s";

  private final ConfigurationClient client;

  @Override
  public String getConfigValue(String configName, String defaultValue) {
    ConfigurationCollection configurations = client.getConfiguration(String.format(CONFIG_QUERY, MODULE_NAME, configName));

    if (configurations.getTotalRecords() == 0) {
      return defaultValue;
    } else {
      Configuration configuration = configurations.getConfigurations().get(0);
      return configuration.getValue();
    }
  }
}
