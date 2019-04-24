package org.folio.type;

import static org.folio.util.DbUtils.ALL_FIELDS;
import static org.folio.util.DbUtils.createParams;
import static org.folio.util.DbUtils.getCQLWrapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.ext.sql.UpdateResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.z3950.zing.cql.cql2pgjson.FieldException;

import org.folio.rest.jaxrs.model.NoteType;
import org.folio.rest.jaxrs.model.NoteTypeCollection;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.cql.CQLWrapper;
import org.folio.rest.persist.interfaces.Results;

@Component
public class NoteTypeRepositoryImpl implements NoteTypeRepository {

  private static final String NOTE_TYPE_TABLE = "note_type";

  private Vertx vertx;


  @Autowired
  public NoteTypeRepositoryImpl(Vertx vertx) {
    this.vertx = vertx;
  }

  @Override
  public Future<NoteTypeCollection> findByQuery(String query, int offset, int limit, String tenantId) {
    CqlQuery<NoteType> q = new CqlQuery<>(pgClient(tenantId), NOTE_TYPE_TABLE, NoteType.class);

    return q.get(query, offset, limit).map(this::toNoteTypeCollection);
  }

  @Override
  public Future<Optional<NoteType>> findById(String id, String tenantId) {
    Future<NoteType> future = Future.future();

    pgClient(tenantId).getById(NOTE_TYPE_TABLE, id, NoteType.class, future);

    return future.map(Optional::ofNullable);
  }

  @Override
  public Future<List<NoteType>> findByIds(List<String> ids, String tenantId) {
    Future<Map<String, NoteType>> future = Future.future();

    pgClient(tenantId).getById(NOTE_TYPE_TABLE, createParams(ids), NoteType.class, future);

    return future.map(resultMap -> new ArrayList<>(resultMap.values()));
  }

  @Override
  public Future<NoteType> save(NoteType entity, String tenantId) {
    Future<String> future = Future.future(); // future with id as result

    pgClient(tenantId).save(NOTE_TYPE_TABLE, entity.getId(), entity, future);

    return future.map(id -> updateId(entity, id)); // update id only, copy the rest from the original entity
  }

  @Override
  public Future<Boolean> update(NoteType entity, String tenantId) {
    Future<UpdateResult> future = Future.future();

    pgClient(tenantId).update(NOTE_TYPE_TABLE, entity, entity.getId(), future);

    return future.map(updateResult -> updateResult.getUpdated() == 1);
  }

  @Override
  public Future<Boolean> delete(String id, String tenantId) {
    Future<UpdateResult> future = Future.future();

    pgClient(tenantId).delete(NOTE_TYPE_TABLE, id, future);

    return future.map(updateResult -> updateResult.getUpdated() == 1);
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

  private static class CqlQuery<T> {

    private PostgresClient pg;
    private String table;
    private Class<T> clazz;


    CqlQuery(PostgresClient pg, String table, Class<T> clazz) {
      this.pg = pg;
      this.table = table;
      this.clazz = clazz;
    }

    Future<Results<T>> get(String cqlQuery, int offset, int limit) {
      CQLWrapper cql;
      try {
        cql = getCQLWrapper(table, cqlQuery, limit, offset);
      } catch (FieldException e) {
        return Future.failedFuture(e);
      }

      Future<Results<T>> future = Future.future();
      pg.get(table, clazz, ALL_FIELDS, cql, true, false, future);

      return future;
    }

  }

}
