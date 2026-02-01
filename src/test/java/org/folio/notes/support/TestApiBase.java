package org.folio.notes.support;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static java.util.Objects.requireNonNull;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.github.tomakehurst.wiremock.WireMockServer;
import java.util.UUID;
import lombok.SneakyThrows;
import org.apache.http.HttpStatus;
import org.folio.notes.domain.dto.User;
import org.folio.spring.FolioModuleMetadata;
import org.folio.spring.integration.XOkapiHeaders;
import org.folio.spring.testing.extension.EnableOkapi;
import org.folio.spring.testing.extension.EnablePostgres;
import org.folio.spring.testing.extension.impl.OkapiConfiguration;
import org.folio.spring.testing.type.IntegrationTest;
import org.folio.tenant.domain.dto.TenantAttributes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

@EnableOkapi
@EnablePostgres
@AutoConfigureMockMvc
@SpringBootTest
@ContextConfiguration
@ActiveProfiles("test")
@IntegrationTest
public abstract class TestApiBase extends TestBase {

  protected static final String TENANT = "test";
  protected static final UUID USER_ID = UUID.randomUUID();

  protected static final JsonMapper OBJECT_MAPPER;

  protected static OkapiConfiguration okapi;

  static {
    OBJECT_MAPPER = JsonMapper.builder()
      .changeDefaultPropertyInclusion(incl -> incl.withValueInclusion(JsonInclude.Include.NON_NULL))
      .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
      .configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true)
      .build();
  }

  @Autowired
  protected MockMvc mockMvc;
  @Autowired
  protected DatabaseHelper databaseHelper;
  protected WireMockServer okapiServer = okapi.wireMockServer();
  @Value("${folio.okapi-url}")
  protected String okapiUrl;
  @Autowired
  private CacheManager cacheManager;

  @SneakyThrows
  public static String asJsonString(Object value) {
    return OBJECT_MAPPER.writeValueAsString(value);
  }

  public static HttpHeaders defaultHeaders() {
    final HttpHeaders httpHeaders = new HttpHeaders();

    httpHeaders.setContentType(APPLICATION_JSON);
    httpHeaders.add(XOkapiHeaders.TENANT, TENANT);
    httpHeaders.add(XOkapiHeaders.USER_ID, USER_ID.toString());

    return httpHeaders;
  }

  public HttpHeaders okapiHeaders() {
    final HttpHeaders httpHeaders = defaultHeaders();
    httpHeaders.add(XOkapiHeaders.URL, okapiUrl);
    return httpHeaders;
  }

  @SneakyThrows
  protected static void setUpTenant(MockMvc mockMvc) {
    mockMvc.perform(post("/_/tenant").content(asJsonString(new TenantAttributes().moduleTo("mod-notes")))
        .headers(defaultHeaders())
        .contentType(APPLICATION_JSON))
      .andExpect(status().isNoContent());
  }

  @SneakyThrows
  protected void stubUser(User user) {
    okapiServer.stubFor(get(urlPathMatching("/users/.*"))
      .willReturn(aResponse().withBody(OBJECT_MAPPER.writeValueAsString(user))
        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
        .withStatus(HttpStatus.SC_OK)));
  }

  protected void stubUserClientError(int status) {
    okapiServer.stubFor(get(urlPathMatching("/users/.*"))
      .willReturn(aResponse().withBody("random message").withStatus(status)));
  }

  @BeforeAll
  static void beforeAll(@Autowired MockMvc mockMvc) {
    setUpTenant(mockMvc);
  }

  @AfterEach
  void cleanupCache() {
    cacheManager.getCacheNames().forEach(name -> requireNonNull(cacheManager.getCache(name)).clear());
  }

  @TestConfiguration
  static class TestConfig {

    @Bean
    public DatabaseHelper databaseHelper(JdbcTemplate jdbcTemplate, FolioModuleMetadata moduleMetadata) {
      return new DatabaseHelper(moduleMetadata, jdbcTemplate);
    }
  }
}
