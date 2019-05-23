package org.folio.rest.impl;

import static io.vertx.core.Future.succeededFuture;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import javax.ws.rs.core.Response;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.ext.sql.SQLConnection;
import io.vertx.ext.sql.UpdateResult;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.mutable.MutableObject;

import org.folio.rest.RestVerticle;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.Link;
import org.folio.rest.jaxrs.model.Note;
import org.folio.rest.jaxrs.model.NoteCollection;
import org.folio.rest.jaxrs.model.NoteLinkPut;
import org.folio.rest.jaxrs.model.NoteLinksPut;
import org.folio.rest.jaxrs.resource.NoteLinks;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.tools.utils.TenantTool;
import org.folio.rest.tools.utils.ValidationHelper;

public class NoteLinksImpl implements NoteLinks {

  private static final String NOTE_TABLE = "note_data";
  private static final String INSERT_LINKS =
    "UPDATE %s " +
    "SET jsonb = jsonb_insert(jsonb, '{links, -1}', ?, true) " +
    "WHERE NOT EXISTS (SELECT FROM jsonb_array_elements(jsonb->'links') link WHERE link = ? ) AND " +
    "id IN (%s)";

  /**
   * in this query, jsonb_set function replaces old jsonb->links array with new one,
   * "-" is an operator that removes an element by index
   * and (select MIN(position)-1 ...) is a subquery that calculates index of first element that matches searched link
   */
  private static final String REMOVE_LINKS =
    "UPDATE %s " +
      "SET jsonb = jsonb_set(jsonb, '{links}',  " +
      "(jsonb->'links') " +
      " - " +
      "(SELECT MIN(position)-1 FROM jsonb_array_elements(jsonb->'links') WITH ORDINALITY links(link, position) WHERE link = ?)::int) " +
    "WHERE id IN (%s) " +
      "AND EXISTS (SELECT FROM jsonb_array_elements(jsonb->'links') link WHERE link = ?)";

  private static final String DELETE_NOTES_WITHOUT_LINKS =
    "DELETE FROM %s " +
    "WHERE id IN (%s) AND " +
    "jsonb->'links' = '[]'::jsonb";

  private static final String SELECT_NOTES_BY_STATUS_ASSIGNED = "SELECT id, jsonb FROM %s WHERE (jsonb->>'domain' = ?) AND " +
    "EXISTS (SELECT FROM jsonb_array_elements(jsonb->'links') link WHERE link = ?) LIMIT ? OFFSET ?";

  private static final String SELECT_NOTES_BY_STATUS_UNASSIGNED = "SELECT id, jsonb FROM %s WHERE (jsonb->>'domain' = ?) AND " +
    "NOT EXISTS (SELECT FROM jsonb_array_elements(jsonb->'links') link WHERE link = ?) LIMIT ? OFFSET ?";

  private static final String SELECT_NOTES_BY_STATUS_ALL = "SELECT id, jsonb FROM %s WHERE (jsonb->>'domain' = ?) " +
    "ORDER BY (CASE WHEN EXISTS (SELECT FROM jsonb_array_elements(jsonb->'links') link WHERE link = ?) THEN 'ASSIGNED'" +
    "ELSE 'UNASSIGNED' END) %s LIMIT ? OFFSET ?";

  private static final Map<String, String> SELECT_QUERIES_MAP = new HashMap<>();

  static {
    SELECT_QUERIES_MAP.put("ASSIGNED", SELECT_NOTES_BY_STATUS_ASSIGNED);
    SELECT_QUERIES_MAP.put("UNASSIGNED", SELECT_NOTES_BY_STATUS_UNASSIGNED);
    SELECT_QUERIES_MAP.put("ALL", SELECT_NOTES_BY_STATUS_ALL);
  }

  @Override
  public void putNoteLinksTypeIdByTypeAndId(String type, String id,
                                               NoteLinksPut entity, Map<String, String> okapiHeaders,
                                               Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    if(entity.getNotes().isEmpty()){
      handleSuccess(asyncResultHandler);
    }
    String tenantId = TenantTool.calculateTenantId(okapiHeaders.get(RestVerticle.OKAPI_HEADER_TENANT));

    PostgresClient postgresClient = PostgresClient.getInstance(vertxContext.owner(), tenantId);

    List<String> assignNotes = getNoteIdsByStatus(entity, NoteLinkPut.Status.ASSIGNED);
    List<String> unAssignNotes = getNoteIdsByStatus(entity, NoteLinkPut.Status.UNASSIGNED);
    Link link = new Link()
      .withId(id)
      .withType(type);

    MutableObject<AsyncResult<SQLConnection>> connection = new MutableObject<>();
    startTransaction(postgresClient)
      .compose(resultConnection -> {
        connection.setValue(resultConnection);
        return assignToNotes(assignNotes, link, postgresClient, connection.getValue(), tenantId);
      })
      .compose(o -> unAssignFromNotes(unAssignNotes, link, postgresClient, connection.getValue(), tenantId))
      .compose(result -> endTransaction(postgresClient, connection.getValue()))
      .compose(result -> {
        handleSuccess(asyncResultHandler);
        return null;
      })
      //recover is used to do a rollback and keep processing failed Future after rollback
      .recover(e -> rollbackTransaction(postgresClient, connection, e))
      .otherwise(e -> {
        asyncResultHandler.handle(succeededFuture(PutNoteLinksTypeIdByTypeAndIdResponse.respond500WithTextPlain(e.getMessage())));
        return null;
      });
  }

  @Validate
  @Override
  public void getNoteLinksDomainTypeIdByDomainAndTypeAndId(String domain, String type, String id, String status, String order,
                                                           int offset, int limit, Map<String, String> okapiHeaders,
                                                           Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    String tenantId = TenantTool.calculateTenantId(okapiHeaders.get(RestVerticle.OKAPI_HEADER_TENANT));

    PostgresClient postgresClient = PostgresClient.getInstance(vertxContext.owner(), tenantId);
    Link link = new Link()
      .withId(id)
      .withType(type);
    try {
      validateParameters(status, order);
    } catch (IllegalArgumentException e) {
      asyncResultHandler.handle(succeededFuture(GetNoteLinksDomainTypeIdByDomainAndTypeAndIdResponse
        .respond400WithTextPlain(e.getMessage())));
    }
    String query = String.format(SELECT_QUERIES_MAP.get(status), getTableName(tenantId), order);
    JsonArray parameters = createSelectParameters(domain, link, limit, offset);
    getNoteCollection(postgresClient, parameters, query)
      .map(notes -> {
        asyncResultHandler.handle(succeededFuture(GetNoteLinksDomainTypeIdByDomainAndTypeAndIdResponse.respond200WithApplicationJson(notes)));
        return null;
      })
      .otherwise(e -> {
        ValidationHelper.handleError(e, asyncResultHandler);
        return null;
      });
  }

  private Future<NoteCollection> getNoteCollection(PostgresClient postgresClient, JsonArray parameters, String query) {
    List<Note> notes = new ArrayList<>();
    NoteCollection noteCollection = new NoteCollection();
    ObjectMapper objectMapper = new ObjectMapper();
    Future<UpdateResult> future = Future.future();

    postgresClient.select(query, parameters, event -> {
      event.result().getRows().forEach(o -> {
        try {
          notes.add(objectMapper.readValue(o.getString("jsonb"), Note.class));
        } catch (IOException e) {
          future.fail(e);
        }
      });
      noteCollection.setNotes(notes);
      noteCollection.setTotalRecords(event.result().getNumRows());
      future.complete();
    });
    return future.map(noteCollection);
  }

  private JsonArray createSelectParameters(String domain, Link link, int limit, int offset) {
    String jsonLink = Json.encode(link);
    JsonArray parameters = new JsonArray();
    parameters
      .add(domain)
      .add(jsonLink)
      .add(limit)
      .add(offset);
    return parameters;
  }

  private void validateParameters(String status, String order) throws IllegalArgumentException {
    if (Objects.isNull(SELECT_QUERIES_MAP.get(status))) {
      throw new IllegalArgumentException("Status is incorrect. Possible values: \"ASSIGNED\",\"UNASSIGNED\",\"ALL\"");
    }
    if (!order.equals("asc") && !order.equals("desc")) {
      throw new IllegalArgumentException("Order is incorrect. Possible values: \"asc\",\"desc\"");
    }
  }

  private void handleSuccess(Handler<AsyncResult<Response>> asyncResultHandler) {
    asyncResultHandler.handle(succeededFuture(PutNoteLinksTypeIdByTypeAndIdResponse.respond204()));
  }

  /**
   * Rollback transaction and return failed future with either specified exception
   * or rollback exception that contains initial exception as suppressed
   */
  private Future<Object> rollbackTransaction(PostgresClient postgresClient, MutableObject<AsyncResult<SQLConnection>> connection, Throwable e) {
    if (connection.getValue() != null) {
      Future<Object> future = Future.future();
      postgresClient.rollbackTx(connection.getValue(), rollback -> {
        if(rollback.failed()){
          Throwable rollbackException = rollback.cause();
          rollbackException.addSuppressed(e);
          future.fail(rollbackException);
        }
        else {
          future.fail(e);
        }
      });
      return future;
    }
    return Future.failedFuture(e);
  }

  private List<String> getNoteIdsByStatus(NoteLinksPut entity, NoteLinkPut.Status status) {
    return entity.getNotes().stream()
      .filter(note -> status.equals(note.getStatus()))
      .map(NoteLinkPut::getId)
      .collect(Collectors.toList());
  }

  private Future<AsyncResult<SQLConnection>> startTransaction(PostgresClient postgresClient){
    Future<AsyncResult<SQLConnection>> future = Future.future();
    postgresClient.startTx(future::complete);
    return future;
  }

  private Future<Void> endTransaction(PostgresClient postgresClient, AsyncResult<SQLConnection> connection){
    Future<Void> future = Future.future();
    postgresClient.endTx(connection , future.completer());
    return future;
  }

  private Future<Void> assignToNotes(List<String> notesIds, Link linkToAssign, PostgresClient postgresClient, AsyncResult<SQLConnection> connection, String tenantId) {
    if(notesIds.isEmpty()){
      return Future.succeededFuture(null);
    }
    String placeholders = createIdPlaceholders(notesIds.size());
    String query = String.format(INSERT_LINKS, getTableName(tenantId), placeholders);
    JsonArray parameters = createAssignParameters(notesIds, linkToAssign);

    Future<UpdateResult> future = Future.future();
    postgresClient.execute(connection, query, parameters, future.completer());
    return future.map(result -> null);
  }

  private Future<Void> unAssignFromNotes(List<String> notesIds, Link link, PostgresClient postgresClient,
                                         AsyncResult<SQLConnection> connection, String tenantId) {
    if(notesIds.isEmpty()){
      return Future.succeededFuture(null);
    }
    String placeholders = createIdPlaceholders(notesIds.size());
    String query = String.format(REMOVE_LINKS, getTableName(tenantId), placeholders);
    JsonArray parameters = createUnAssignParameters(notesIds, link);

    Future<UpdateResult> future = Future.future();
    postgresClient.execute(connection, query, parameters, future.completer());
    return future
      .compose(o -> deleteNotesWithoutLinks(notesIds, postgresClient, connection, tenantId))
      .map(result -> null);
  }

  private Future<Void> deleteNotesWithoutLinks(List<String> notesIds, PostgresClient postgresClient,
                                               AsyncResult<SQLConnection> connection, String tenantId) {
    if(notesIds.isEmpty()){
      return Future.succeededFuture(null);
    }

    String placeholders = createIdPlaceholders(notesIds.size());
    String query = String.format(DELETE_NOTES_WITHOUT_LINKS, getTableName(tenantId), placeholders);
    JsonArray parameters = new JsonArray();
    notesIds.forEach(parameters::add);

    Future<UpdateResult> future = Future.future();
    postgresClient.execute(connection, query, parameters, future.completer());
    return future
      .map(result -> null);
  }

  private JsonArray createAssignParameters(List<String> notesIds, Link link) {
    String jsonLink = Json.encode(link);
    JsonArray parameters = new JsonArray();
    parameters
      .add(jsonLink)
      .add(jsonLink);
    notesIds.forEach(parameters::add);
    return parameters;
  }

  private JsonArray createUnAssignParameters(List<String> notesIds, Link link) {
    String jsonLink = Json.encode(link);
    JsonArray parameters = new JsonArray();
    parameters
      .add(jsonLink);
    notesIds.forEach(parameters::add);
    parameters.add(jsonLink);
    return parameters;
  }

  private String getTableName(String tenantId) {
    return PostgresClient.convertToPsqlStandard(tenantId) + "." + NOTE_TABLE;
  }

  private String createIdPlaceholders(int amountOfIds) {
    return StringUtils.join(Collections.nCopies(amountOfIds, "?"), ", ");
  }
}
