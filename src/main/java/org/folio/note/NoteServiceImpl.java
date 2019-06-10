package org.folio.note;

import static io.vertx.core.Future.failedFuture;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.ws.rs.BadRequestException;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.core.Response;

import org.folio.common.OkapiParams;
import org.folio.db.model.NoteView;
import org.folio.rest.RestVerticle;
import org.folio.rest.exceptions.InputValidationException;
import org.folio.rest.jaxrs.model.Link;
import org.folio.rest.jaxrs.model.Note;
import org.folio.rest.jaxrs.model.NoteCollection;
import org.folio.rest.jaxrs.model.UserDisplayInfo;
import org.folio.rest.persist.PgExceptionUtil;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.Criteria.Limit;
import org.folio.rest.persist.Criteria.Offset;
import org.folio.rest.persist.cql.CQLWrapper;
import org.folio.rest.persist.interfaces.Results;
import org.folio.rest.tools.utils.TenantTool;
import org.folio.userlookup.UserLookUp;
import org.springframework.stereotype.Component;
import org.z3950.zing.cql.cql2pgjson.CQL2PgJSON;
import org.z3950.zing.cql.cql2pgjson.CQL2PgJSONException;
import org.z3950.zing.cql.cql2pgjson.FieldException;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.sql.UpdateResult;
import io.vertx.ext.web.handler.impl.HttpStatusException;

@Component
public class NoteServiceImpl implements NoteService {
  private static final String NOTE_VIEW = "note_view";
  private static final String NOTE_TABLE = "note_data";

  private final Logger logger = LoggerFactory.getLogger(NoteServiceImpl.class);

  @Override
  public Future<NoteCollection> getNotes(Context vertxContext, String tenantId, CQLWrapper cql) {
    Future<Results<NoteView>> future = Future.future();
    PostgresClient.getInstance(vertxContext.owner(), tenantId)
      .get(NOTE_VIEW, NoteView.class, new String[]{"*"}, cql,
        true /*get count too*/, false /* set id */,
        future.completer());

    return future
      .map(this::mapNoteResults);
  }

  @Override
  public Future<NoteCollection> getNotes(String query, int offset, int limit, Context vertxContext, String tenantId) {
    logger.debug("Getting notes. new query:" + query);
    Future<NoteCollection> future = Future.succeededFuture(null);
    return future.compose(o -> {
      CQLWrapper cql;
      try {
        cql = getCQLForNoteView(query, limit, offset);
      } catch (CQL2PgJSONException e) {
        throw new IllegalArgumentException("Failed to parse cql query");
      }
      return getNotes(vertxContext, tenantId, cql);
    });
  }

  @Override
  public Future<Note> addNote(Note note, UserLookUp creatorUser, Vertx vertx, OkapiParams okapiParams) {
    final List<Link> links = note.getLinks();
    if (Objects.isNull(links) || links.isEmpty()) {
      throw new InputValidationException("links", "links", "At least one link should be present");
    }
    note.setCreator(getUserDisplayInfo(creatorUser.getFirstName(), creatorUser.getMiddleName(), creatorUser.getLastName()));
    note.getMetadata().setCreatedByUsername(creatorUser.getUserName());
    return saveNote(note, vertx, okapiParams.getTenant());
  }

  /**
   * Fetches a note record from the database
   *
   * @param id id of note to get
   */
  @Override
  public Future<Note> getOneNote(String id, String tenantId, Vertx vertx) {
    Future<Note> future = Future.future();
    PostgresClient.getInstance(vertx, tenantId)
      .getById(NOTE_VIEW, id, NoteView.class,
        reply -> {
          if (reply.succeeded()) {
            NoteView noteView = reply.result();
            if (Objects.isNull(noteView)) {
              future.fail(new HttpStatusException(Response.Status.NOT_FOUND.getStatusCode(), "Note " + id + " not found"));
            } else {
              future.complete(mapNoteView(noteView));
            }
          } else {
            String error = PgExceptionUtil.badRequestMessage(reply.cause());
            logger.error(error, reply.cause());
            if (error == null) {
              future.fail(new HttpStatusException(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), reply.cause().getMessage()));
            } else {
              future.fail(new HttpStatusException(Response.Status.BAD_REQUEST.getStatusCode(), error));
            }
          }
        });
    return future;
  }

  @Override
  public Future<Void> deleteNote(String id, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext, String tenantId) {
    Future<UpdateResult> future = Future.future();
    PostgresClient.getInstance(vertxContext.owner(), tenantId)
      .delete(NOTE_TABLE, id, future.completer());
    return future.map(updateResult -> {
      if(updateResult.getUpdated() == 0){
        throw new NotFoundException();
      }
      return null;
    });
  }

  @Override
  public Future<Void> updateNoteWithUser(String id, Note note, Map<String, String> okapiHeaders, Context vertxContext) {
    return setNoteUpdater(note, UserLookUp.getUserInfo(okapiHeaders))
      .compose(voidObject -> updateNote(id, note, okapiHeaders, vertxContext));
  }


  private Future<Void> setNoteUpdater(Note note, Future<UserLookUp> userInfo) {
    return userInfo
      .map(userLookUp -> {
        final UserDisplayInfo userDisplayInfo = getUserDisplayInfo(userLookUp.getFirstName(), userLookUp.getMiddleName(), userLookUp.getLastName());
        note.setUpdater(userDisplayInfo);
        note.getMetadata().setUpdatedByUsername(userLookUp.getUserName());
        return null;
      });
  }

  private Future<Void> updateNote(String id, Note note, Map<String, String> okapiHeaders, Context vertxContext) {
    Future<UpdateResult> future = Future.future();
    String tenantId = TenantTool.calculateTenantId(okapiHeaders.get(RestVerticle.OKAPI_HEADER_TENANT));
    if (note.getLinks().isEmpty()) {
      PostgresClient.getInstance(vertxContext.owner(), tenantId)
        .delete(NOTE_TABLE, id, future.completer());
    } else {
      PostgresClient.getInstance(vertxContext.owner(), tenantId)
        .update(NOTE_TABLE, note, id, future.completer());
    }
    return future
      .map(updateResult -> {
        if(updateResult.getUpdated() == 0){
          throw new NotFoundException();
        }
        return (Void) null;
      })
      .recover(throwable -> {
        String badRequestMessage = PgExceptionUtil.badRequestMessage(throwable);
        if (badRequestMessage != null) {
          return failedFuture(new BadRequestException(badRequestMessage, throwable));
        } else {
          return failedFuture(throwable);
        }
      });
  }


  private CQLWrapper getCQLForNoteView(String query, int limit, int offset) throws FieldException {
    CQL2PgJSON cql2pgJson = new CQL2PgJSON(NOTE_VIEW + ".jsonb");
    return new CQLWrapper(cql2pgJson, query)
      .setLimit(new Limit(limit))
      .setOffset(new Offset(offset));
  }


  private NoteCollection mapNoteResults(Results<NoteView> results) {
    List<Note> notes = results.getResults().stream()
      .map(this::mapNoteView)
      .collect(Collectors.toList());

    NoteCollection noteCollection = new NoteCollection();
    noteCollection.setNotes(notes);
    Integer totalRecords = results.getResultInfo().getTotalRecords();
    noteCollection.setTotalRecords(totalRecords);
    return noteCollection;
  }

  private Note mapNoteView(NoteView noteView) {
    return new Note()
      .withId(noteView.getId())
      .withTypeId(noteView.getTypeId())
      .withType(noteView.getType())
      .withDomain(noteView.getDomain())
      .withTitle(noteView.getTitle())
      .withContent(noteView.getContent())
      .withCreator(noteView.getCreator())
      .withUpdater(noteView.getUpdater())
      .withMetadata(noteView.getMetadata())
      .withLinks(noteView.getLinks());
  }

  private UserDisplayInfo getUserDisplayInfo(String firstName, String middleName, String lastName) {
    final UserDisplayInfo userDisplayInfo = new UserDisplayInfo();
    userDisplayInfo.setFirstName(firstName);
    userDisplayInfo.setMiddleName(middleName);
    userDisplayInfo.setLastName(lastName);
    return userDisplayInfo;
  }

  /**
   * Saves a note record to the database
   *
   * @param note - current Note  {@link Note} object to save
   */
  private Future<Note> saveNote(Note note, Vertx vertx, String tenantId) {
    initId(note);
    Future<String> future = Future.future();
    PostgresClient.getInstance(vertx, tenantId)
      .save(NOTE_TABLE, note.getId(), note, future.completer());

    return future.map(noteId -> {
      note.setId(noteId);
      return note;
    });
  }

  /**
   * Sets a note record random UUID
   *
   * @param note - current Note {@link Note} object
   */
  private void initId(Note note) {
    String noteId = note.getId();
    if (noteId == null || noteId.isEmpty()) {
      note.setId(UUID.randomUUID().toString());
    }
  }
}
