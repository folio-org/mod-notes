package org.folio.notes.support;

import static org.mockito.ArgumentMatchers.anyString;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import feign.FeignException;
import feign.Request;
import feign.Response;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.util.Map;
import lombok.SneakyThrows;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Testcontainers;

import org.folio.notes.client.ConfigurationClient;
import org.folio.notes.client.ConfigurationClient.ConfigurationEntry;
import org.folio.notes.client.ConfigurationClient.ConfigurationEntryCollection;
import org.folio.spring.FolioModuleMetadata;
import org.folio.spring.integration.XOkapiHeaders;
import org.folio.tenant.domain.dto.TenantAttributes;

@Testcontainers
@DirtiesContext
@ContextConfiguration
@AutoConfigureMockMvc
@SpringBootTest
public abstract class TestApiBase extends TestBase {

  protected static final String TENANT = "test";
  protected static final String USER_ID = "77777777-7777-7777-7777-777777777777";

  protected static final ObjectMapper OBJECT_MAPPER;

  static {
    postgreDBContainer.start();
    OBJECT_MAPPER = new ObjectMapper().setSerializationInclusion(JsonInclude.Include.NON_NULL)
      .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
      .configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true)
      .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
      .setSerializationInclusion(JsonInclude.Include.NON_NULL)
      .registerModule(new JavaTimeModule());
  }

  @Autowired
  protected MockMvc mockMvc;
  @Autowired
  protected DatabaseHelper databaseHelper;
  @MockBean
  protected ConfigurationClient configurationClient;

  @BeforeAll
  static void beforeAll(@Autowired MockMvc mockMvc) {
    setUpTenant(mockMvc);
  }

  @AfterAll
  static void afterAll() {
    postgreDBContainer.stop();
  }

  @SneakyThrows
  protected static void setUpTenant(MockMvc mockMvc) {
    mockMvc.perform(post("/_/tenant").content(asJsonString(new TenantAttributes().moduleTo("mod-notes")))
        .headers(defaultHeaders())
        .contentType(APPLICATION_JSON))
      .andExpect(status().isNoContent());
  }

  @SneakyThrows
  public static String asJsonString(Object value) {
    return OBJECT_MAPPER.writeValueAsString(value);
  }

  public static HttpHeaders defaultHeaders() {
    final HttpHeaders httpHeaders = new HttpHeaders();

    httpHeaders.setContentType(APPLICATION_JSON);
    httpHeaders.add(XOkapiHeaders.TENANT, TENANT);
    httpHeaders.add(XOkapiHeaders.USER_ID, USER_ID);

    return httpHeaders;
  }

  protected void setUpConfigurationLimit(String limit) {
    List<ConfigurationEntry> configurations = new ArrayList<>();
    if (!limit.isBlank()) {
      var configuration = new ConfigurationEntry("1", "NOTES", "note-type-limit", limit);
      configurations.add(configuration);
    }
    var configs = new ConfigurationEntryCollection(configurations, configurations.size());

    Mockito.doReturn(configs).when(configurationClient)
      .getConfiguration("module==NOTES and configName==note-type-limit");
  }

  protected void stubConfigurationClientError(int status, String message) {
    Map<String, Collection<String>> headers = new HashMap<>();
    Mockito.when(configurationClient.getConfiguration(anyString()))
      .thenThrow(FeignException.errorStatus("getConfiguration",
        Response.builder()
          .status(status)
          .headers(headers)
          .reason(message)
          .request(Request.create(
            Request.HttpMethod.GET,
            "configurations/entries", headers,
            null, null, null))
          .build()));
  }

  @TestConfiguration
  static class TestConfig {

    @Bean
    public DatabaseHelper databaseHelper(JdbcTemplate jdbcTemplate, FolioModuleMetadata moduleMetadata) {
      return new DatabaseHelper(moduleMetadata, jdbcTemplate);
    }
  }
}
