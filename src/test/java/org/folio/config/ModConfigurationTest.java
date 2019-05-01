package org.folio.config;

import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import static org.folio.util.TestUtil.mockGet;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;

import com.github.tomakehurst.wiremock.common.Slf4jNotifier;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.github.tomakehurst.wiremock.matching.RegexPattern;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.apache.http.HttpStatus;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;
import org.junit.runner.RunWith;

import org.folio.okapi.common.XOkapiHeaders;
import org.folio.util.OkapiParams;

@RunWith(VertxUnitRunner.class)
public class ModConfigurationTest {

  private static final String STUB_TENANT = "testlib";
  private static final String STUB_TOKEN = "TEST_OKAPI_TOKEN";
  private static final String HOST = "http://127.0.0.1";

  private static final String CONFIG_MODULE = "NOTES";
  private static final String CONFIG_PROP_CODE = "note.types.number.limit";
  private static final RegexPattern CONFIG_NOTE_TYPE_LIMIT_URL_PATTERN =
    new RegexPattern("/configurations/entries.*");

  private static final Logger logger = LoggerFactory.getLogger(ModConfigurationTest.class);

  @Rule
  public TestRule watcher = new TestWatcher() {

    @Override
    protected void starting(Description description) {
      logger.info("********** Running test method: {}.{} ********** ", description.getClassName(),
        description.getMethodName());
    }

  };

  @Rule
  public WireMockRule mockServer = new WireMockRule(
    WireMockConfiguration.wireMockConfig()
      .dynamicPort()
      .notifier(new Slf4jNotifier(true)));

  private OkapiParams okapiParams;
  private ModConfiguration configuration;


  @Before
  public void setUp() {
    Map<String, String> headers = new HashMap<>();
    headers.put(XOkapiHeaders.TENANT, STUB_TENANT);
    headers.put(XOkapiHeaders.TOKEN, STUB_TOKEN);
    headers.put(XOkapiHeaders.URL, HOST + ":" + mockServer.port());

    okapiParams = new OkapiParams(headers);
    configuration = new ModConfiguration(CONFIG_MODULE);
  }

  @Test(expected = IllegalArgumentException.class)
  public void failIfModuleNotProvidedToConstructor() {
    new ModConfiguration("");
  }

  @Test
  public void shouldReturnPropAsString(TestContext context) throws IOException, URISyntaxException {
    mockGet(CONFIG_NOTE_TYPE_LIMIT_URL_PATTERN, "responses/configuration/get-note-type-limit-5-response.json");

    Handler<AsyncResult<String>> verify = context.asyncAssertSuccess(result -> {
      assertNotNull(result);
      assertEquals("5", result);
    });

    configuration.getString(CONFIG_PROP_CODE, okapiParams).setHandler(verify);
  }

  @Test
  public void shouldReturnPropAsInt(TestContext context) throws IOException, URISyntaxException {
    mockGet(CONFIG_NOTE_TYPE_LIMIT_URL_PATTERN, "responses/configuration/get-note-type-limit-5-response.json");

    Handler<AsyncResult<Integer>> verify = context.asyncAssertSuccess(result -> {
      assertNotNull(result);
      assertEquals(5, result.intValue());
    });

    configuration.getInt(CONFIG_PROP_CODE, okapiParams).setHandler(verify);
  }

  @Test
  public void shouldReturnPropAsLong(TestContext context) throws IOException, URISyntaxException {
    mockGet(CONFIG_NOTE_TYPE_LIMIT_URL_PATTERN, "responses/configuration/get-note-type-limit-5-response.json");

    Handler<AsyncResult<Long>> verify = context.asyncAssertSuccess(result -> {
      assertNotNull(result);
      assertEquals(5L, result.longValue());
    });

    configuration.getLong(CONFIG_PROP_CODE, okapiParams).setHandler(verify);
  }

  @Test
  public void shouldReturnPropAsDouble(TestContext context) throws IOException, URISyntaxException {
    mockGet(CONFIG_NOTE_TYPE_LIMIT_URL_PATTERN, "responses/configuration/get-note-type-limit-5-response.json");

    Handler<AsyncResult<Double>> verify = context.asyncAssertSuccess(result -> {
      assertNotNull(result);
      assertEquals(5d, result, 0);
    });

    configuration.getDouble(CONFIG_PROP_CODE, okapiParams).setHandler(verify);
  }

  @Test
  public void shouldReturnDefaultInCaseOfFailure(TestContext context) {
    mockGet(CONFIG_NOTE_TYPE_LIMIT_URL_PATTERN, HttpStatus.SC_BAD_REQUEST);

    Handler<AsyncResult<String>> verify = context.asyncAssertSuccess(result -> {
      assertNotNull(result);
      assertEquals("10", result);
    });

    configuration.getString(CONFIG_PROP_CODE, "10", okapiParams).setHandler(verify);
  }

  @Test
  public void failIfPropNotFound(TestContext context) throws IOException, URISyntaxException {
    mockGet(CONFIG_NOTE_TYPE_LIMIT_URL_PATTERN, "responses/configuration/get-note-type-limit-empty-response.json");

    Handler<AsyncResult<String>> verify = context.asyncAssertFailure(t -> {
      assertTrue(t instanceof PropertyNotFoundException);
      assertEquals(CONFIG_PROP_CODE, ((PropertyNotFoundException) t).getPropertyCode());
    });

    configuration.getString(CONFIG_PROP_CODE, okapiParams).setHandler(verify);
  }

  @Test
  public void failIfPropDisabled(TestContext context) throws IOException, URISyntaxException {
    mockGet(CONFIG_NOTE_TYPE_LIMIT_URL_PATTERN, "responses/configuration/get-note-type-limit-disabled-response.json");

    Handler<AsyncResult<String>> verify = context.asyncAssertFailure(t -> {
      assertTrue(t instanceof PropertyNotFoundException);
      assertEquals(CONFIG_PROP_CODE, ((PropertyNotFoundException) t).getPropertyCode());
    });

    configuration.getString(CONFIG_PROP_CODE, okapiParams).setHandler(verify);
  }

  @Test
  public void failIfResponseNotOk(TestContext context) {
    mockGet(CONFIG_NOTE_TYPE_LIMIT_URL_PATTERN, HttpStatus.SC_BAD_REQUEST);

    Handler<AsyncResult<String>> verify = context.asyncAssertFailure(t -> {
      assertTrue(t instanceof ConfigurationException);
      assertThat(t.getMessage(), containsString(String.valueOf(HttpStatus.SC_BAD_REQUEST)));
    });

    configuration.getString(CONFIG_PROP_CODE, okapiParams).setHandler(verify);
  }

  @Test
  public void failIfNoValueInConfig(TestContext context) throws IOException, URISyntaxException {
    mockGet(CONFIG_NOTE_TYPE_LIMIT_URL_PATTERN, "responses/configuration/get-note-type-limit-no-value-response.json");

    Handler<AsyncResult<String>> verify = context.asyncAssertFailure(t -> {
      assertTrue(t instanceof ValueNotDefinedException);
      assertEquals(CONFIG_PROP_CODE, ((ValueNotDefinedException) t).getPropertyCode());
    });

    configuration.getString(CONFIG_PROP_CODE, okapiParams).setHandler(verify);
  }

  @Test
  public void failIfSeveralPropsPresent(TestContext context) throws IOException, URISyntaxException {
    mockGet(CONFIG_NOTE_TYPE_LIMIT_URL_PATTERN, "responses/configuration/get-note-type-limit-not-unique-response.json");

    Handler<AsyncResult<String>> verify = context.asyncAssertFailure(t -> {
      assertTrue(t instanceof PropertyException);
      assertEquals(CONFIG_PROP_CODE, ((PropertyException) t).getPropertyCode());
      assertThat(t.getMessage(), containsString("more than one configuration properties found"));
    });

    configuration.getString(CONFIG_PROP_CODE, okapiParams).setHandler(verify);
  }
}
