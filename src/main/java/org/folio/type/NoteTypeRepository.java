package org.folio.type;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.folio.rest.persist.PostgresClient;
import org.folio.rest.tools.utils.TenantTool;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.sql.ResultSet;

public class NoteTypeRepository {

  private static final String NAME_COLUMN = "name";
  private static final String ID_COLUMN = "id";
  private static final String NOTE_TYPE_TABLE = "note_type";
  private static final String SELECT_TYPE_NAMES_BY_IDS =
    "SELECT " + ID_COLUMN + ", jsonb::json->>'" + NAME_COLUMN + "' AS " + NAME_COLUMN + " FROM %s" +
      " WHERE " + ID_COLUMN + " IN (%s)";

  private final Logger logger = LoggerFactory.getLogger(NoteTypeRepository.class);

  public Future<Map<String, String>> getTypesByIds(List<String> ids, Map<String, String> okapiHeaders, Context vertxContext, String tenantId) {
    if (ids.isEmpty()) {
      return Future.succeededFuture(Collections.emptyMap());
    }
    Future<ResultSet> future = Future.future();

    JsonArray parameters = new JsonArray(ids);
    String parameterPlaceholders = String.join(",", Collections.nCopies(ids.size(), "?"));

    String query =
      String.format(SELECT_TYPE_NAMES_BY_IDS, getFullTableName(tenantId, NOTE_TYPE_TABLE), parameterPlaceholders);

    logger.info("Do select query to get type names = {} for ids {}", query, parameters);
    PostgresClient.getInstance(vertxContext.owner(), TenantTool.tenantId(okapiHeaders))
      .select(query, parameters, future.completer());

    return future
      .map(resultSet -> {
        Map<String, String> nameMap = new HashMap<>();
        resultSet.getRows()
          .forEach(row -> nameMap.put(row.getString(ID_COLUMN), row.getString(NAME_COLUMN)));
        return nameMap;
      });
  }

  private String getFullTableName(String tenantId, String tableName) {
    return PostgresClient.convertToPsqlStandard(tenantId) + "." + tableName;
  }
}
