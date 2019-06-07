package org.folio.links;

import static org.folio.links.NoteLinksConstants.DELETE_NOTES_WITHOUT_LINKS;
import static org.folio.links.NoteLinksConstants.INSERT_LINKS;
import static org.folio.links.NoteLinksConstants.REMOVE_LINKS;
import static org.folio.links.NoteLinksDbQueryHelper.addLimitOffset;
import static org.folio.links.NoteLinksDbQueryHelper.addOrderByClause;
import static org.folio.links.NoteLinksDbQueryHelper.addSelectClause;
import static org.folio.links.NoteLinksDbQueryHelper.addSelectCountClause;
import static org.folio.links.NoteLinksDbQueryHelper.addWhereClause;
import static org.folio.links.NoteLinksDbQueryHelper.createAssignParameters;
import static org.folio.links.NoteLinksDbQueryHelper.createIdPlaceholders;
import static org.folio.links.NoteLinksDbQueryHelper.createUnAssignParameters;
import static org.folio.links.NoteLinksDbQueryHelper.getTableName;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.ext.sql.ResultSet;
import io.vertx.ext.sql.SQLConnection;
import io.vertx.ext.sql.UpdateResult;
import org.apache.commons.lang3.mutable.MutableObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.folio.rest.jaxrs.model.Link;
import org.folio.rest.jaxrs.model.Note;
import org.folio.rest.jaxrs.model.NoteCollection;
import org.folio.rest.model.Order;
import org.folio.rest.model.OrderBy;
import org.folio.rest.model.Status;
import org.folio.rest.persist.PostgresClient;

@Component
public class NoteLinksRepositoryImpl implements NoteLinksRepository {

  private Vertx vertx;

  @Autowired
  public NoteLinksRepositoryImpl(Vertx vertx) {
    this.vertx = vertx;
  }

  @Override
  public Future<Void> putNoteLinkTypeIdToNote(Link link, String tenantId, List<String> assignNotes, List<String> unAssignNotes) {
    PostgresClient postgresClient = pgClient(tenantId);
    MutableObject<AsyncResult<SQLConnection>> connection = new MutableObject<>();

    return startTransaction(postgresClient).compose(resultConnection -> {
      connection.setValue(resultConnection);
      return assignToNotes(assignNotes, link, postgresClient, connection.getValue(), tenantId);
    }).compose(o -> unAssignFromNotes(unAssignNotes, link, postgresClient, connection.getValue(),
      tenantId)).compose(result -> endTransaction(postgresClient, connection.getValue()))
      // recover is used to do a rollback and keep processing failed Future after rollback
      .recover(e -> rollbackTransaction(postgresClient, connection, e));
  }

  @Override
  public Future<NoteCollection> getNoteCollection(Status status, String tenantId, Order order,
                                                  OrderBy orderBy, String domain, String title, Link link, int limit, int offset) {
    String jsonLink = Json.encode(link);
    JsonArray parameters = new JsonArray();
    StringBuilder queryBuilder = new StringBuilder();
    addSelectClause(parameters, queryBuilder, tenantId, domain, title);
    addWhereClause(parameters, queryBuilder, status, jsonLink);
    if (status == Status.ALL) {
      addOrderByClause(parameters, queryBuilder, order, orderBy, jsonLink);
    }
    addLimitOffset(parameters, queryBuilder, limit, offset);

    Future<ResultSet> future = Future.future();
    pgClient(tenantId).select(queryBuilder.toString(), parameters, future.completer());
    return future.map(this::mapResultToNoteCollection);
  }

  @Override
  public Future<Integer> getNoteCount(Status status, String domain, String title, Link link,
                                      String tenantId) {
    String jsonLink = Json.encode(link);
    JsonArray parameters = new JsonArray();
    StringBuilder queryBuilder = new StringBuilder();
    addSelectCountClause(parameters, queryBuilder, tenantId, domain, title);
    addWhereClause(parameters, queryBuilder, status, jsonLink);

    Future<ResultSet> future = Future.future();
    pgClient(tenantId).select(queryBuilder.toString(), parameters, future.completer());
    return future.map(this::mapCount);
  }

  private Future<Void> assignToNotes(List<String> notesIds, Link linkToAssign, PostgresClient postgresClient,
                                     AsyncResult<SQLConnection> connection, String tenantId) {
    if (notesIds.isEmpty()) {
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
    if (notesIds.isEmpty()) {
      return Future.succeededFuture(null);
    }
    String placeholders = createIdPlaceholders(notesIds.size());
    String query = String.format(REMOVE_LINKS, getTableName(tenantId), placeholders);
    JsonArray parameters = createUnAssignParameters(notesIds, link);

    Future<UpdateResult> future = Future.future();
    postgresClient.execute(connection, query, parameters, future.completer());
    return future.compose(o -> deleteNotesWithoutLinks(notesIds, postgresClient, connection, tenantId)).map(
      result -> null);
  }

  private Future<Void> deleteNotesWithoutLinks(List<String> notesIds, PostgresClient postgresClient,
                                               AsyncResult<SQLConnection> connection, String tenantId) {
    if (notesIds.isEmpty()) {
      return Future.succeededFuture(null);
    }

    String placeholders = createIdPlaceholders(notesIds.size());
    String query = String.format(DELETE_NOTES_WITHOUT_LINKS, getTableName(tenantId), placeholders);
    JsonArray parameters = new JsonArray();
    notesIds.forEach(parameters::add);

    Future<UpdateResult> future = Future.future();
    postgresClient.execute(connection, query, parameters, future.completer());
    return future.map(result -> null);
  }

  private NoteCollection mapResultToNoteCollection(ResultSet results) {
    List<Note> notes = new ArrayList<>();
    ObjectMapper objectMapper = new ObjectMapper();
    NoteCollection noteCollection = new NoteCollection();
    results.getRows().forEach(object -> {
      try {
        notes.add(objectMapper.readValue(object.getString("jsonb"), Note.class));
      } catch (IOException e) {
        throw new IllegalStateException(e);
      }
      noteCollection.setNotes(notes);
    });
    return noteCollection;
  }

  /**
   * Rollback transaction and return failed future with either specified exception
   * or rollback exception that contains initial exception as suppressed
   */

  private Future<Void> rollbackTransaction(PostgresClient postgresClient,
                                           MutableObject<AsyncResult<SQLConnection>> connection, Throwable e) {
    if (connection.getValue() != null) {
      Future<Void> future = Future.future();
      postgresClient.rollbackTx(connection.getValue(), rollback -> {
        if (rollback.failed()) {
          Throwable rollbackException = rollback.cause();
          rollbackException.addSuppressed(e);
          future.fail(rollbackException);
        } else {
          future.fail(e);
        }
      });
      return future;
    }
    return Future.failedFuture(e);
  }

  private Future<AsyncResult<SQLConnection>> startTransaction(PostgresClient postgresClient) {
    Future<AsyncResult<SQLConnection>> future = Future.future();
    postgresClient.startTx(future::complete);
    return future;
  }

  private Future<Void> endTransaction(PostgresClient postgresClient, AsyncResult<SQLConnection> connection) {
    Future<Void> future = Future.future();
    postgresClient.endTx(connection, future.completer());
    return future;
  }

  private Integer mapCount(ResultSet resultSet) {
    return resultSet.getRows().get(0).getInteger("count");
  }

  private PostgresClient pgClient(String tenantId) {
    return PostgresClient.getInstance(vertx, tenantId);
  }
}
