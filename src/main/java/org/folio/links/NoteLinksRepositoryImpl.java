package org.folio.links;

import static io.vertx.core.Future.succeededFuture;

import static org.folio.links.NoteLinksConstants.ANY_STRING_PATTERN;
import static org.folio.links.NoteLinksConstants.COUNT_NOTES_BY_DOMAIN_AND_TITLE;
import static org.folio.links.NoteLinksConstants.DELETE_NOTES_WITHOUT_LINKS;
import static org.folio.links.NoteLinksConstants.HAS_LINK_CONDITION;
import static org.folio.links.NoteLinksConstants.INSERT_LINKS;
import static org.folio.links.NoteLinksConstants.LIMIT_OFFSET;
import static org.folio.links.NoteLinksConstants.NOTE_TABLE;
import static org.folio.links.NoteLinksConstants.NOTE_TYPE_TABLE;
import static org.folio.links.NoteLinksConstants.ORDER_BY_LINKS_NUMBER;
import static org.folio.links.NoteLinksConstants.ORDER_BY_NOTE_TYPE;
import static org.folio.links.NoteLinksConstants.ORDER_BY_STATUS_CLAUSE;
import static org.folio.links.NoteLinksConstants.ORDER_BY_TITLE_CLAUSE;
import static org.folio.links.NoteLinksConstants.ORDER_BY_UPDATED_DATE;
import static org.folio.links.NoteLinksConstants.REMOVE_LINKS;
import static org.folio.links.NoteLinksConstants.SELECT_NOTES_BY_DOMAIN_AND_TITLE;
import static org.folio.links.NoteLinksConstants.WHERE_CLAUSE_BY_NOTE_TYPE;
import static org.folio.links.NoteLinksConstants.WORD_PATTERN;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.Tuple;
import io.vertx.sqlclient.impl.ArrayTuple;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.mutable.MutableObject;
import org.springframework.stereotype.Component;

import org.folio.model.EntityLink;
import org.folio.model.Order;
import org.folio.model.OrderBy;
import org.folio.model.RowPortion;
import org.folio.model.Status;
import org.folio.rest.jaxrs.model.Link;
import org.folio.rest.jaxrs.model.Note;
import org.folio.rest.jaxrs.model.NoteCollection;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.SQLConnection;

@Component
public class NoteLinksRepositoryImpl implements NoteLinksRepository {

  private static final String SPECIAL_REGEX_SYMBOLS = "!$()*+.:<=>?[]\\^{|}-";
  private static final String ESCAPED_ANY_STRING_WILDCARD = "\\*";

  private final Vertx vertx;

  public NoteLinksRepositoryImpl(Vertx vertx) {
    this.vertx = vertx;
  }

  @Override
  public Future<Void> update(Link link, List<String> assignNotes, List<String> unAssignNotes, String tenantId) {
    PostgresClient postgresClient = pgClient(tenantId);
    MutableObject<AsyncResult<SQLConnection>> connection = new MutableObject<>();

    return startTransaction(postgresClient)
      .compose(resultConnection -> {
        connection.setValue(resultConnection);
        return assignToNotes(assignNotes, link, postgresClient, connection.getValue(), tenantId);
      })
      .compose(o -> unAssignFromNotes(unAssignNotes, link, postgresClient, connection.getValue(), tenantId))
      .compose(result -> endTransaction(postgresClient, connection.getValue()))
      // recover is used to do a rollback and keep processing failed Future after rollback
      .recover(e -> rollbackTransaction(postgresClient, connection, e));
  }

  @Override
  public Future<NoteCollection> findNotesByTitleAndNoteTypeAndStatus(EntityLink link, String title, List<String> noteTypes,
                                                                     Status status,
                                                                     OrderBy orderBy, Order order, RowPortion rowPortion,
                                                                     String tenantId) {
    Tuple parameters = Tuple.tuple();
    StringBuilder queryBuilder = new StringBuilder();

    addSelectClause(parameters, queryBuilder, link.getDomain(), title, tenantId);

    addWhereNoteTypeClause(parameters, queryBuilder, noteTypes);

    JsonObject jsonLink = JsonObject.mapFrom(toLink(link));
    addWhereClause(parameters, queryBuilder, status, jsonLink);

    addOrderByClause(parameters, queryBuilder, order, orderBy, jsonLink);

    addLimitOffset(parameters, queryBuilder, rowPortion);

    Promise<RowSet<Row>> promise = Promise.promise();
    String query = prepareQuery(queryBuilder.toString());
    pgClient(tenantId).select(query, parameters, promise);

    return promise.future().map(this::mapResultToNoteCollection);
  }

  @Override
  public Future<Integer> countNotesByTitleAndNoteTypeAndStatus(EntityLink link, String title, List<String> noteTypes,
                                                               Status status, String tenantId) {

    Tuple parameters = Tuple.tuple();
    StringBuilder queryBuilder = new StringBuilder();

    addSelectCountClause(parameters, queryBuilder, link.getDomain(), title, tenantId);

    addWhereNoteTypeClause(parameters, queryBuilder, noteTypes);

    JsonObject jsonLink = JsonObject.mapFrom(toLink(link));
    addWhereClause(parameters, queryBuilder, status, jsonLink);

    Promise<Row> promise = Promise.promise();
    String query = prepareQuery(queryBuilder.toString());
    pgClient(tenantId).selectSingle(query, parameters, promise);

    return promise.future().map(result -> result.getInteger("count"));
  }

  private void addWhereNoteTypeClause(Tuple parameters, StringBuilder query, List<String> noteTypes) {
    noteTypes.replaceAll(String::trim);

    if (noteTypes.stream().allMatch(StringUtils::isBlank)) {
      return;
    }

    query.append(String.format(WHERE_CLAUSE_BY_NOTE_TYPE, createIdPlaceholders(noteTypes.size())));
    noteTypes.forEach(parameters::addString);
  }

  private Future<Void> assignToNotes(List<String> notesIds, Link linkToAssign, PostgresClient postgresClient,
                                     AsyncResult<SQLConnection> connection, String tenantId) {
    if (notesIds.isEmpty()) {
      return succeededFuture(null);
    }
    String placeholders = createIdPlaceholders(notesIds.size());
    String query = prepareQuery(String.format(INSERT_LINKS, getNoteTableName(tenantId), placeholders));
    Tuple parameters = createAssignParameters(notesIds, linkToAssign);

    Promise<RowSet<Row>> promise = Promise.promise();
    postgresClient.execute(connection, query, parameters, promise);

    return promise.future().map(result -> null);
  }

  private Future<Void> unAssignFromNotes(List<String> notesIds, Link link, PostgresClient postgresClient,
                                         AsyncResult<SQLConnection> connection, String tenantId) {
    if (notesIds.isEmpty()) {
      return succeededFuture(null);
    }
    String placeholders = createIdPlaceholders(notesIds.size());
    String query = prepareQuery(String.format(REMOVE_LINKS, getNoteTableName(tenantId), placeholders));
    Tuple parameters = createUnAssignParameters(notesIds, link);

    Promise<RowSet<Row>> promise = Promise.promise();
    postgresClient.execute(connection, query, parameters, promise);

    return promise.future().compose(o -> deleteNotesWithoutLinks(notesIds, postgresClient, connection, tenantId))
      .map(result -> null);
  }

  private Future<Void> deleteNotesWithoutLinks(List<String> notesIds, PostgresClient postgresClient,
                                               AsyncResult<SQLConnection> connection, String tenantId) {
    if (notesIds.isEmpty()) {
      return succeededFuture(null);
    }

    String placeholders = createIdPlaceholders(notesIds.size());
    String query = prepareQuery(String.format(DELETE_NOTES_WITHOUT_LINKS, getNoteTableName(tenantId), placeholders));
    Tuple parameters = Tuple.tuple();
    addUUIDsToParams(notesIds, parameters);

    Promise<RowSet<Row>> promise = Promise.promise();
    postgresClient.execute(connection, query, parameters, promise);

    return promise.future().map(result -> null);
  }

  private void addUUIDsToParams(List<String> ids, Tuple parameters) {
    ids.stream()
      .map(UUID::fromString)
      .forEach(parameters::addUUID);
  }

  private NoteCollection mapResultToNoteCollection(RowSet<Row> results) {
    List<Note> notes = new ArrayList<>();
    ObjectMapper objectMapper = new ObjectMapper();
    NoteCollection noteCollection = new NoteCollection();
    results.forEach(object -> {
      try {
        notes.add(objectMapper.readValue(object.getValue("jsonb").toString(), Note.class));
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
      Promise<Void> promise = Promise.promise();
      postgresClient.rollbackTx(connection.getValue(), rollback -> {
        if (rollback.failed()) {
          Throwable rollbackException = rollback.cause();
          rollbackException.addSuppressed(e);
          promise.fail(rollbackException);
        } else {
          promise.fail(e);
        }
      });
      return promise.future();
    }
    return Future.failedFuture(e);
  }

  private Future<AsyncResult<SQLConnection>> startTransaction(PostgresClient postgresClient) {
    Promise<AsyncResult<SQLConnection>> promise = Promise.promise();
    postgresClient.startTx(promise::complete);
    return promise.future();
  }

  private Future<Void> endTransaction(PostgresClient postgresClient, AsyncResult<SQLConnection> connection) {
    Promise<Void> promise = Promise.promise();
    postgresClient.endTx(connection, promise);
    return promise.future();
  }

  private Link toLink(EntityLink link) {
    return new Link().withType(link.getType()).withId(link.getId());
  }

  private PostgresClient pgClient(String tenantId) {
    return PostgresClient.getInstance(vertx, tenantId);
  }

  private String getNoteTableName(String tenantId) {
    return PostgresClient.convertToPsqlStandard(tenantId) + "." + NOTE_TABLE;
  }

  private String getNoteTypeTableName(String tenantId) {
    return PostgresClient.convertToPsqlStandard(tenantId) + "." + NOTE_TYPE_TABLE;
  }

  private String createIdPlaceholders(int amountOfIds) {
    return StringUtils.join(Collections.nCopies(amountOfIds, "?"), ", ");
  }

  private Tuple createAssignParameters(List<String> notesIds, Link link) {
    JsonObject jsonLink = JsonObject.mapFrom(link);
    Tuple parameters = new ArrayTuple(notesIds.size() + 2);
    parameters
      .addValue(jsonLink)
      .addValue(jsonLink);
    addUUIDsToParams(notesIds, parameters);
    return parameters;
  }

  private Tuple createUnAssignParameters(List<String> notesIds, Link link) {
    JsonObject jsonLink = JsonObject.mapFrom(link);
    Tuple parameters = new ArrayTuple(notesIds.size() + 2);
    parameters
      .addValue(jsonLink);
    addUUIDsToParams(notesIds, parameters);
    parameters.addValue(jsonLink);
    return parameters;
  }

  private void addLimitOffset(Tuple parameters, StringBuilder query, RowPortion rowPortion) {
    query.append(LIMIT_OFFSET);
    parameters
      .addInteger(rowPortion.getLimit())
      .addInteger(rowPortion.getOffset());
  }

  private void addSelectClause(Tuple parameters, StringBuilder query, String domain, String title, String tenantId) {
    query
      .append(String.format(SELECT_NOTES_BY_DOMAIN_AND_TITLE, getNoteTableName(tenantId), getNoteTypeTableName(tenantId)));
    parameters
      .addString(domain)
      .addString(getTitleRegexp(title));
  }

  private void addSelectCountClause(Tuple parameters, StringBuilder query, String domain, String title, String tenantId) {
    query.append(String.format(COUNT_NOTES_BY_DOMAIN_AND_TITLE, getNoteTableName(tenantId), getNoteTypeTableName(tenantId)));
    parameters
      .addString(domain)
      .addString(getTitleRegexp(title));
  }

  private void addOrderByClause(Tuple parameters, StringBuilder query, Order order, OrderBy orderBy, JsonObject jsonLink) {
    if (orderBy == OrderBy.STATUS) {
      query.append(String.format(ORDER_BY_STATUS_CLAUSE, order.toString()));
      parameters.addValue(jsonLink);
    } else if (orderBy == OrderBy.LINKSNUMBER) {
      query.append(String.format(ORDER_BY_LINKS_NUMBER, order.toString()));
    } else if (orderBy == OrderBy.NOTETYPE) {
      query.append(String.format(ORDER_BY_NOTE_TYPE, order.toString()));
    } else if (orderBy == OrderBy.UPDATEDDATE) {
      query.append(String.format(ORDER_BY_UPDATED_DATE, order.toString()));
    } else {
      query.append(String.format(ORDER_BY_TITLE_CLAUSE, order.toString()));
    }
  }

  private void addWhereClause(Tuple parameters, StringBuilder query, Status status, JsonObject jsonLink) {
    switch (status) {
      case ASSIGNED:
        query.append("AND " + HAS_LINK_CONDITION);
        parameters.addValue(jsonLink);
        break;
      case UNASSIGNED:
        query.append("AND NOT " + HAS_LINK_CONDITION);
        parameters.addValue(jsonLink);
        break;
      default: // do nothing
    }
  }

  private String getTitleRegexp(String title) {
    if (StringUtils.isEmpty(title)) {
      return ANY_STRING_PATTERN;
    } else {
      String regex = escapeRegex(title)
        .replace(ESCAPED_ANY_STRING_WILDCARD, ".*");
      return String.format(WORD_PATTERN, regex);
    }
  }

  private String escapeRegex(String str) {
    return str
      .replaceAll("[\\Q" + SPECIAL_REGEX_SYMBOLS + "\\E]", "\\\\$0");
  }

  private String prepareQuery(String query) {
    StringBuilder sb = new StringBuilder(query);
    int index = 1;
    int i = 0;
    while ((i = sb.indexOf("?", i)) != -1) {
      sb.replace(i, i + 1, "$" + index++);
    }
    return sb.toString();
  }
}
