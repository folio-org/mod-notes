package org.folio.config;

import io.vertx.core.Future;

import org.folio.util.OkapiParams;

public interface Configuration {

  Future<String> getString(String code, OkapiParams params);

  Future<String> getString(String code, String def, OkapiParams params);

  Future<Integer> getInt(String code, OkapiParams params);

  Future<Integer> getInt(String code, int def, OkapiParams params);

  Future<Long> getLong(String code, OkapiParams params);

  Future<Long> getLong(String code, long def, OkapiParams params);

  Future<Double> getDouble(String code, OkapiParams params);

  Future<Double> getDouble(String code, double def, OkapiParams params);

  Future<Boolean> getBoolean(String code, OkapiParams params);

  Future<Boolean> getBoolean(String code, boolean def, OkapiParams params);

}
