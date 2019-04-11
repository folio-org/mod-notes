package org.folio.type;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.folio.rest.persist.PostgresClient;
import org.folio.rest.tools.utils.TenantTool;

import io.vertx.core.Context;
import io.vertx.core.json.JsonArray;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public class NoteTypeRepository {

  private static final String NAME_COLUMN = "name";
  private static final String ID_COLUMN = "id";
  private static final String NOTE_TYPE_TABLE = "note_type";
  private static final String SELECT_TYPE_NAMES_BY_IDS =
    "SELECT " + ID_COLUMN + ", jsonb::json->>'" + NAME_COLUMN + "' AS " + NAME_COLUMN + " FROM %s" +
      " WHERE " + ID_COLUMN + " IN (%s)";

  private final Logger logger = LoggerFactory.getLogger(NoteTypeRepository.class);

  public CompletableFuture<Map<String, String>> getTypesByIds(List<String> ids, Map<String, String> okapiHeaders, Context vertxContext, String tenantId) {
    if(ids.isEmpty()){
      return CompletableFuture.completedFuture(Collections.emptyMap());
    }
    CompletableFuture<Map<String, String>> future = new CompletableFuture<>();

    JsonArray parameters = new JsonArray(ids);
    String parameterPlaceholders = String.join(",", Collections.nCopies(ids.size(), "?"));

    String query =
      String.format(SELECT_TYPE_NAMES_BY_IDS, getFullTableName(tenantId, NOTE_TYPE_TABLE), parameterPlaceholders);

    logger.info("Do select query to get type names = {} for ids {}", query, parameters);
    PostgresClient.getInstance(vertxContext.owner(), TenantTool.tenantId(okapiHeaders))
      .select(query, parameters,  result -> {
        if(result.succeeded()){
          Map<String, String> nameMap = new HashMap<>();
          result.result().getRows()
            .forEach(row -> nameMap.put(row.getString(ID_COLUMN), row.getString(NAME_COLUMN)));
          future.complete(nameMap);
        }else{
          future.completeExceptionally(result.cause());
        }
      });

    return future;
  }

  private String getFullTableName(String tenantId, String tableName) {
    return PostgresClient.convertToPsqlStandard(tenantId) + "." + tableName;
  }
}
