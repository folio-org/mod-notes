package org.folio.rest.impl;

import static io.vertx.core.Future.succeededFuture;

import java.util.Map;

import javax.ws.rs.BadRequestException;
import javax.ws.rs.NotAuthorizedException;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.core.Response;

import org.folio.common.OkapiParams;
import org.folio.note.NoteService;
import org.folio.rest.RestVerticle;
import org.folio.rest.annotations.Validate;
import org.folio.rest.exceptions.InputValidationException;
import org.folio.rest.jaxrs.model.Errors;
import org.folio.rest.jaxrs.model.Note;
import org.folio.rest.jaxrs.resource.Notes;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.tools.utils.TenantTool;
import org.folio.rest.tools.utils.ValidationHelper;
import org.folio.spring.SpringContextUtil;
import org.springframework.beans.factory.annotation.Autowired;

import com.github.mauricio.async.db.postgresql.exceptions.GenericDatabaseException;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.Json;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.handler.impl.HttpStatusException;

public class NotesResourceImpl implements Notes {
  private static final String LOCATION_PREFIX = "/notes/";
  private static final String IDFIELDNAME = "id";
  private final Logger logger = LoggerFactory.getLogger("mod-notes");

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

    noteService.getNotes(query, offset, limit, tenantId)
      .map(notes -> {
        asyncResultHandler.handle(succeededFuture(GetNotesResponse.respond200WithApplicationJson(notes)));
        return null;
      })
      .otherwise(e -> {
        ValidationHelper.handleError(e, asyncResultHandler);
        return null;
      });
  }

  @Override
  @Validate
  public void postNotes(String lang, Note note, Map<String, String> okapiHeaders,
                        Handler<AsyncResult<Response>> asyncResultHandler, Context context) {
    OkapiParams okapiParams = new OkapiParams(okapiHeaders);

      noteService.addNote(note, okapiParams)
      .map(updatedNote -> {
        asyncResultHandler.handle(succeededFuture(PostNotesResponse
          .respond201WithApplicationJson(note,
            PostNotesResponse.headersFor201().withLocation(LOCATION_PREFIX + updatedNote.getId()))));
        return null;
      })
      .otherwise(exception -> {
        if (exception instanceof GenericDatabaseException) {
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

  @Override
  @Validate
  public void getNotesById(String id, String lang, Map<String, String> okapiHeaders,
                           Handler<AsyncResult<Response>> asyncResultHandler, Context context) {
    String tenantId = TenantTool.calculateTenantId(okapiHeaders.get(RestVerticle.OKAPI_HEADER_TENANT));
    noteService.getOneNote(id, tenantId)
      .map(note -> {
        asyncResultHandler.handle(succeededFuture(GetNotesByIdResponse
          .respond200WithApplicationJson(note)));
        return null;
      })
      .otherwise(exception -> {
        if (exception instanceof HttpStatusException) {
          final int cause = ((HttpStatusException) exception).getStatusCode();
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
    String tenantId = TenantTool.calculateTenantId(
      okapiHeaders.get(RestVerticle.OKAPI_HEADER_TENANT));
    noteService.deleteNote(id, tenantId)
      .map(response -> {
        asyncResultHandler.handle(succeededFuture(DeleteNotesByIdResponse.respond204()));
        return null;
      })
      .otherwise(e -> {
        if (e instanceof NotFoundException) {
          asyncResultHandler.handle(succeededFuture(DeleteNotesByIdResponse.respond404WithTextPlain("Not found")));
        } else {
          asyncResultHandler.handle(succeededFuture(DeleteNotesByIdResponse.respond500WithTextPlain(e.getMessage())));
        }
        return null;
      });
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

    OkapiParams okapiParams = new OkapiParams(okapiHeaders);

    noteService.updateNote(id, note, okapiParams)
      .map(o -> {
        asyncResultHandler.handle(succeededFuture(PutNotesByIdResponse.respond204()));
        return null;
      })
      .otherwise(exception -> {
        if (exception instanceof NotFoundException) {
          asyncResultHandler.handle(succeededFuture(PutNotesByIdResponse.respond404WithTextPlain(exception.getMessage())));
        }
        if (exception instanceof NotAuthorizedException ||
          exception instanceof IllegalArgumentException || exception instanceof IllegalStateException ||
          exception instanceof BadRequestException) {
          asyncResultHandler.handle(succeededFuture(PutNotesByIdResponse.respond400WithTextPlain(exception.getMessage())));
        } else {
          asyncResultHandler.handle(succeededFuture(PutNotesByIdResponse.respond500WithTextPlain(exception.getMessage())));
        }
        return null;
      });
  }
}
