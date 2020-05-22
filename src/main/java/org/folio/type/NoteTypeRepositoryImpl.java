package org.folio.type;

import static org.folio.db.DbUtils.createParams;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.folio.db.CqlQuery;
import org.folio.db.exc.translation.DBExceptionTranslator;
import org.folio.rest.jaxrs.model.NoteType;
import org.folio.rest.jaxrs.model.NoteTypeCollection;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.interfaces.Results;

@Component
public class NoteTypeRepositoryImpl implements NoteTypeRepository {

  private static final String NOTE_TYPE_TABLE = "note_type";
  private static final String NOTE_TYPE_VIEW = "note_type_view";
  private static final String SELECT_TOTAL_COUNT = "SELECT count(*) FROM " + NOTE_TYPE_TABLE;

  @Autowired
  private Vertx vertx;
  @Autowired
  private DBExceptionTranslator excTranslator;


  @Override
  public Future<NoteTypeCollection> findByQuery(String query, int offset, int limit, String tenantId) {
    CqlQuery<NoteType> q = new CqlQuery<>(pgClient(tenantId), NOTE_TYPE_VIEW, NoteType.class);

    return q.get(query, offset, limit)
      .recover(excTranslator.translateOrPassBy())
      .map(this::toNoteTypeCollection);
  }

  @Override
  public Future<Optional<NoteType>> findById(String id, String tenantId) {
    Promise<NoteType> promise = Promise.promise();

    pgClient(tenantId).getById(NOTE_TYPE_VIEW, id, NoteType.class, promise);

    return promise.future()
      .recover(excTranslator.translateOrPassBy())
      .map(Optional::ofNullable);
  }

  @Override
  public Future<List<NoteType>> findByIds(List<String> ids, String tenantId) {
    Promise<Map<String, NoteType>> promise = Promise.promise();

    pgClient(tenantId).getById(NOTE_TYPE_VIEW, createParams(ids), NoteType.class, promise);

    return promise.future()
      .recover(excTranslator.translateOrPassBy())
      .map(resultMap -> new ArrayList<>(resultMap.values()));
  }

  @Override
  public Future<Long> count(String tenantId) {
    Promise<Row> promise = Promise.promise();

    pgClient(tenantId).selectSingle(SELECT_TOTAL_COUNT, promise);

    return promise.future().recover(excTranslator.translateOrPassBy())
      .map(result -> result.getLong(0));
  }

  @Override
  public Future<NoteType> save(NoteType entity, String tenantId) {
    Promise<String> promise = Promise.promise(); // promise with id as result

    if (StringUtils.isBlank(entity.getId())) {
      entity.setId(UUID.randomUUID().toString());
    }

    pgClient(tenantId).save(NOTE_TYPE_TABLE, entity.getId(), entity, promise);

    return promise.future().recover(excTranslator.translateOrPassBy())
      .map(id -> updateId(entity, id)); // update id only, copy the rest from the original entity
  }

  @Override
  public Future<Boolean> update(NoteType entity, String tenantId) {
    Promise<RowSet<Row>> promise = Promise.promise();

    pgClient(tenantId).update(NOTE_TYPE_TABLE, entity, entity.getId(), promise);

    return promise.future().recover(excTranslator.translateOrPassBy())
      .map(updateResult -> updateResult.rowCount() == 1);
  }

  @Override
  public Future<Boolean> delete(String id, String tenantId) {
    Promise<RowSet<Row>> promise = Promise.promise();

    pgClient(tenantId).delete(NOTE_TYPE_TABLE, id, promise);

    return promise.future().recover(excTranslator.translateOrPassBy())
      .map(updateResult -> updateResult.rowCount() == 1);
  }

  private PostgresClient pgClient(String tenantId) {
    return PostgresClient.getInstance(vertx, tenantId);
  }

  private NoteTypeCollection toNoteTypeCollection(Results<NoteType> results) {
    return new NoteTypeCollection()
      .withNoteTypes(results.getResults())
      .withTotalRecords(results.getResultInfo().getTotalRecords());
  }

  private NoteType updateId(NoteType entity, String newId) {
    return new NoteType()
      .withId(newId)
      .withName(entity.getName())
      .withUsage(entity.getUsage()) // this field is not cloned deeply
      .withMetadata(entity.getMetadata()); // this field is not cloned deeply
  }
}
