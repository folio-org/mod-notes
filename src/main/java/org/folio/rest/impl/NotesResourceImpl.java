package org.folio.rest.impl;

import static io.vertx.core.Future.succeededFuture;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import javax.ws.rs.BadRequestException;
import javax.ws.rs.NotAuthorizedException;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.core.Response;

import org.folio.db.model.NoteView;
import org.folio.note.NoteService;
import org.folio.rest.RestVerticle;
import org.folio.rest.annotations.Validate;
import org.folio.rest.exceptions.InputValidationException;
import org.folio.rest.jaxrs.model.Errors;
import org.folio.rest.jaxrs.model.Link;
import org.folio.rest.jaxrs.model.Note;
import org.folio.rest.jaxrs.model.UserDisplayInfo;
import org.folio.rest.jaxrs.resource.Notes;
import org.folio.rest.persist.PgExceptionUtil;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.tools.messages.Messages;
import org.folio.rest.tools.utils.TenantTool;
import org.folio.rest.tools.utils.ValidationHelper;
import org.folio.spring.SpringContextUtil;
import org.folio.userlookup.UserLookUp;
import org.springframework.beans.factory.annotation.Autowired;

import com.github.mauricio.async.db.postgresql.exceptions.GenericDatabaseException;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.Json;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.handler.impl.HttpStatusException;

public class NotesResourceImpl implements Notes {
  private static final String NOTE_TABLE = "note_data";
  private static final String NOTE_VIEW = "note_view";
  private static final String LOCATION_PREFIX = "/notes/";
  private static final String IDFIELDNAME = "id";
  private final Logger logger = LoggerFactory.getLogger("mod-notes");
  private final Messages messages = Messages.getInstance();

  @Autowired
  private NoteService noteService;

  // Get this from the restVerticle, like the rest, when it gets defined there.
  public NotesResourceImpl(Vertx vertx, String tenantId) {
    SpringContextUtil.autowireDependencies(this, vertx.getOrCreateContext());
    PostgresClient.getInstance(vertx, tenantId).setIdField(IDFIELDNAME);
  }

  @Override
  @Validate
  public void getNotes(String query,
                       int offset, int limit, String lang,
                       Map<String, String> okapiHeaders,
                       Handler<AsyncResult<Response>> asyncResultHandler,
                       Context vertxContext) {
    logger.debug("Getting notes. " + offset + "+" + limit + " q=" + query);
    String tenantId = TenantTool.calculateTenantId(
      okapiHeaders.get(RestVerticle.OKAPI_HEADER_TENANT));

    noteService.getNotes(query, offset, limit, vertxContext, tenantId)
      .map(notes -> {
        asyncResultHandler.handle(succeededFuture(GetNotesResponse.respond200WithApplicationJson(notes)));
        return null;
      })
      .otherwise(e -> {
        ValidationHelper.handleError(e, asyncResultHandler);
        return null;
      });
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

  @Override
  @Validate
  public void postNotes(String lang, Note note, Map<String, String> okapiHeaders,
                        Handler<AsyncResult<Response>> asyncResultHandler, Context context) {
    Future<UserLookUp> future = UserLookUp.getUserInfo(okapiHeaders);
    addNote(note, okapiHeaders, context, future)
      .map(updatedNote -> {
        asyncResultHandler.handle(succeededFuture(PostNotesResponse
          .respond201WithApplicationJson(note,
            PostNotesResponse.headersFor201().withLocation(LOCATION_PREFIX + updatedNote.getId()))));
        return null;
      })
      .otherwise(exception -> {
        if(exception instanceof GenericDatabaseException){
          ValidationHelper.handleError(exception, asyncResultHandler);
        } else if (exception instanceof InputValidationException) {
          InputValidationException validationException = (InputValidationException) exception;
          asyncResultHandler.handle(succeededFuture(PostNotesResponse.respond422WithApplicationJson(
            ValidationHelper.createValidationErrorMessage(
              validationException.getField(), validationException.getValue(), validationException.getMessage())
          )));
        } else if (exception instanceof NotFoundException || exception instanceof NotAuthorizedException ||
          exception instanceof IllegalArgumentException || exception instanceof IllegalStateException ||
          exception instanceof BadRequestException) {
          asyncResultHandler.handle(succeededFuture(PostNotesResponse.respond400WithTextPlain(exception.getMessage())));
        } else {
          asyncResultHandler.handle(succeededFuture(PostNotesResponse.respond500WithTextPlain(exception.getMessage())));
        }
        return null;
      });
  }

  private Future<Note> addNote(Note note, Map<String, String> okapiHeaders, Context context, Future<UserLookUp> future) {
    return future.compose(userInfo -> {
      final List<Link> links = note.getLinks();
      if (Objects.isNull(links) || links.isEmpty()) {
        throw new InputValidationException("links", "links", "At least one link should be present");
      }
      return setNoteCreator(note, Future.succeededFuture(userInfo));
    })
    .compose(voidObject -> saveNote(note, okapiHeaders, context));
  }

  /**
   * Saves a note record to the database
   *
   * @param note - current Note  {@link Note} object to save
   * @param okapiHeaders - okapiHeaders headers with tenant id
   * @param context - the Vertx Context Object
   */
  private Future<Note> saveNote(Note note, Map<String, String> okapiHeaders, Context context) {
    String tenantId = TenantTool.calculateTenantId(okapiHeaders.get(RestVerticle.OKAPI_HEADER_TENANT));

    initId(note);
    Future<String> future = Future.future();
    PostgresClient.getInstance(context.owner(), tenantId)
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

  /**
   * Fetches a note record from the database
   *
   * @param id id of note to get
   * @param okapiHeaders okapiHeaders headers with tenant id
   */
  private Future<Note> getOneNote(String id, Map<String, String> okapiHeaders, Context context) {

    Future<Note> future = Future.future();
    String tenantId = TenantTool.calculateTenantId(okapiHeaders.get(RestVerticle.OKAPI_HEADER_TENANT));
    PostgresClient.getInstance(context.owner(), tenantId)
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
  @Validate
  public void getNotesById(String id, String lang, Map<String, String> okapiHeaders,
                           Handler<AsyncResult<Response>> asyncResultHandler, Context context) {
    getOneNote(id, okapiHeaders, context)
      .map(note -> {
        asyncResultHandler.handle(succeededFuture(GetNotesByIdResponse
          .respond200WithApplicationJson(note)));
        return null;
      })
      .otherwise(exception -> {
        if (exception instanceof HttpStatusException) {

          final int cause =  ((HttpStatusException) exception).getStatusCode();

          if (Response.Status.NOT_FOUND.getStatusCode() == cause) {
            asyncResultHandler.handle(succeededFuture(GetNotesByIdResponse.respond404WithTextPlain(
              ((HttpStatusException) exception).getPayload())));
          } else if (Response.Status.BAD_REQUEST.getStatusCode() == cause) {
            asyncResultHandler.handle(succeededFuture(GetNotesByIdResponse.respond400WithTextPlain(
              ((HttpStatusException) exception).getPayload())));
          } else {
            asyncResultHandler.handle(succeededFuture(GetNotesByIdResponse.respond500WithTextPlain(
              exception.getMessage())));
          }
        } else {
          asyncResultHandler.handle(succeededFuture(GetNotesByIdResponse.respond500WithTextPlain(exception.getMessage())));
        }
        return null;
      });
  }

  @Override
  @Validate
  public void deleteNotesById(String id, String lang, Map<String, String> okapiHeaders,
                              Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
   PgUtil.deleteById(NOTE_TABLE,id,okapiHeaders, vertxContext, DeleteNotesByIdResponse.class, asyncResultHandler);
  }


  @Override
  @Validate
  public void putNotesById(String id, String lang, Note note, Map<String, String> okapiHeaders,
                           Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

    logger.debug("PUT note with id:{} and content: {}", id, Json.encode(note));
    if (note.getId() == null) {
      note.setId(id);
      logger.debug("No Id in the note, taking the one from the link");
      // The RMB should handle this. See RMB-94
    }
    if (!note.getId().equals(id)) {
      Errors validationErrorMessage = ValidationHelper.createValidationErrorMessage("id", note.getId(), "Can not change Id");
      asyncResultHandler.handle(succeededFuture(PutNotesByIdResponse.respond422WithApplicationJson(validationErrorMessage)));
      return;
    }

    setNoteUpdater(note, okapiHeaders)
      .compose(voidObject -> updateNote(id, note, okapiHeaders, asyncResultHandler, vertxContext))
      .otherwise(exception -> {
        if (exception instanceof NotFoundException || exception instanceof NotAuthorizedException ||
            exception instanceof IllegalArgumentException || exception instanceof IllegalStateException ||
            exception instanceof BadRequestException) {
          asyncResultHandler.handle(succeededFuture(PutNotesByIdResponse.respond400WithTextPlain(exception.getMessage())));
        } else {
          asyncResultHandler.handle(succeededFuture(PutNotesByIdResponse.respond500WithTextPlain(exception.getMessage())));
        }
        return null;
    });
  }

  private Future<Void> setNoteCreator(Note note, Future<UserLookUp> userInfo) {
    return userInfo
      .map(userLookUp -> {
        note.setCreator(getUserDisplayInfo(userLookUp.getFirstName(), userLookUp.getMiddleName(), userLookUp.getLastName()));
        note.getMetadata().setCreatedByUsername(userLookUp.getUserName());
        return null;
    });
  }

  private Future<Void> setNoteUpdater(Note note, Map<String, String> okapiHeaders) {
    return UserLookUp.getUserInfo(okapiHeaders)
      .map(userLookUp -> {
        final UserDisplayInfo userDisplayInfo = getUserDisplayInfo(userLookUp.getFirstName(), userLookUp.getMiddleName(), userLookUp.getLastName());
        note.setUpdater(userDisplayInfo);
        note.getMetadata().setUpdatedByUsername(userLookUp.getUserName());
        return null;
      });
  }

  private Future<Void> updateNote(String id, Note note, Map<String, String> okapiHeaders,
                          Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

    if (note.getLinks().isEmpty()) {
      PgUtil.deleteById(NOTE_TABLE, id, okapiHeaders, vertxContext, PutNotesByIdResponse.class, asyncResultHandler);
    } else {
      PgUtil.put(NOTE_TABLE, note, id, okapiHeaders, vertxContext, PutNotesByIdResponse.class, asyncResultHandler);
    }
    return null;
  }

  private UserDisplayInfo getUserDisplayInfo(String firstName, String middleName, String lastName) {

    final UserDisplayInfo userDisplayInfo = new UserDisplayInfo();
    userDisplayInfo.setFirstName(firstName);
    userDisplayInfo.setMiddleName(middleName);
    userDisplayInfo.setLastName(lastName);
    return userDisplayInfo;
  }
}
