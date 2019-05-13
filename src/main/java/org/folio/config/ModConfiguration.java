package org.folio.config;

import static java.lang.String.format;
import static org.apache.commons.lang3.ObjectUtils.defaultIfNull;

import static org.folio.util.FutureUtils.wrapExceptions;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.apache.commons.lang3.StringUtils;

import org.folio.rest.client.ConfigurationsClient;
import org.folio.util.OkapiParams;

public class ModConfiguration implements Configuration {

  private static final String PROP_VALUE = "value";
  private static final String PROP_BY_CODE_QUERY = "module==%s AND code==%s";

  private String module;


  public ModConfiguration(String module) {
    if (StringUtils.isBlank(module)) {
      throw new IllegalArgumentException("Module name cannot be empty");
    }
    this.module = module;
  }

  @Override
  public Future<String> getString(String code, OkapiParams params) {
    return getValue(code, value(), params);
  }

  @Override
  public Future<String> getString(String code, String def, OkapiParams params) {
    return getString(code, params).otherwise(def);
  }

  @Override
  public Future<Integer> getInt(String code, OkapiParams params) {
    return getValue(code, value().andThen(convert(Integer::valueOf)), params);
  }

  @Override
  public Future<Integer> getInt(String code, int def, OkapiParams params) {
    return getInt(code, params).otherwise(def);
  }

  @Override
  public Future<Long> getLong(String code, OkapiParams params) {
    return getValue(code, value().andThen(convert(Long::valueOf)), params);
  }

  @Override
  public Future<Long> getLong(String code, long def, OkapiParams params) {
    return getLong(code, params).otherwise(def);
  }

  @Override
  public Future<Double> getDouble(String code, OkapiParams params) {
    return getValue(code, value().andThen(convert(Double::valueOf)), params);
  }

  @Override
  public Future<Double> getDouble(String code, double def, OkapiParams params) {
    return getDouble(code, params).otherwise(def);
  }

  @Override
  public Future<Boolean> getBoolean(String code, OkapiParams params) {
    return getValue(code, value().andThen(convert(Boolean::valueOf)), params);
  }

  @Override
  public Future<Boolean> getBoolean(String code, boolean def, OkapiParams params) {
    return getBoolean(code, params).otherwise(def);
  }

  private <T> Future<T> getValue(String code, Function<JsonObject, Optional<T>> valueExtractor, OkapiParams params) {
    // make sure non config exception is wrapped into ConfigurationException
    return wrapExceptions(
              getConfigObject(code, params)
                .map(valueExtractor)
                .map(value -> failIfEmpty(code, value)),
              ConfigurationException.class);
  }

  private <T> T failIfEmpty(String propertyCode, Optional<T> value) {
    return value.orElseThrow(() -> new ValueNotDefinedException(propertyCode));
  }

  private Future<JsonObject> getConfigObject(String code, OkapiParams params) {
    Future<JsonObject> result = Future.future();

    try {
      ConfigurationsClient configurationsClient = new ConfigurationsClient(params.getHost(), params.getPort(),
        params.getTenant(), params.getToken());

      String query = format(PROP_BY_CODE_QUERY, module, code);

      configurationsClient.getEntries(query, 0, 10, null, null, response ->
        response.bodyHandler(body -> handleResponseBody(response, body, code, result))
      );
    } catch (Exception e) {
      result.fail(e);
    }

    return result;
  }

  private void handleResponseBody(HttpClientResponse response, Buffer body, String code, Future<JsonObject> future) {
    if (response.statusCode() == 200) {
      try {
        future.complete(retrievePropObject(code, body));
      } catch (Exception e) {
        future.fail(e);
      }
    } else {
      future.fail(new ConfigurationException(
        format("Configuration property cannot be retrieved: code = %s. " +
          "Response details: status = %d, body = '%s'", code, response.statusCode(), body.toString())));
    }
  }

  private JsonObject retrievePropObject(String code, Buffer body) {
    JsonObject entries = body.toJsonObject();

    JsonArray configs = defaultIfNull(entries.getJsonArray("configs"), new JsonArray());

    List<JsonObject> enabled = configs.stream()
      .map(JsonObject.class::cast)
      .filter(config -> config.getBoolean("enabled", true))
      .collect(Collectors.toList());

    switch (enabled.size()) {
      case 0: throw new PropertyNotFoundException(code);
      case 1: return enabled.get(0);
      default: throw new PropertyException(code, "more than one configuration properties found");
    }
  }

  private static Function<JsonObject, Optional<String>> value() {
    return json -> {
      String value = json.getString(PROP_VALUE);
      return StringUtils.isNotBlank(value) ? Optional.of(value) : Optional.empty();
    };
  }

  private static <T> Function<Optional<String>, Optional<T>> convert(Function<String, T> conversion) {
    return s -> s.map(conversion);
  }
  
}
