package org.folio.util;

import io.vertx.core.json.JsonArray;
import org.z3950.zing.cql.cql2pgjson.CQL2PgJSON;
import org.z3950.zing.cql.cql2pgjson.FieldException;

import org.folio.rest.persist.Criteria.Limit;
import org.folio.rest.persist.Criteria.Offset;
import org.folio.rest.persist.cql.CQLWrapper;

public final class DbUtils {

  @SuppressWarnings("squid:S2386")
  public static final String[] ALL_FIELDS = {"*"};

  
  private DbUtils() {}

  public static CQLWrapper getCQLWrapper(String tableName, String query, int limit, int offset) throws FieldException {
    return getCQLWrapper(tableName, query)
            .setLimit(new Limit(limit))
            .setOffset(new Offset(offset));
  }

  public static CQLWrapper getCQLWrapper(String tableName, String query) throws FieldException {
    CQL2PgJSON cql2pgJson = new CQL2PgJSON(tableName + ".jsonb");
    return new CQLWrapper(cql2pgJson, query);
  }

  public static JsonArray createParams(Iterable<?> queryParameters) {
    JsonArray parameters = new JsonArray();

    queryParameters.forEach(parameters::add);

    return parameters;
  }
}
