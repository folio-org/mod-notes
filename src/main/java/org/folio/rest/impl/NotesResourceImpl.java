package org.folio.rest.impl;

import static io.vertx.core.Future.succeededFuture;
import static org.folio.okapi.common.ErrorType.INTERNAL;
import static org.folio.okapi.common.ErrorType.NOT_FOUND;
import static org.folio.okapi.common.ErrorType.USER;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import javax.ws.rs.core.Response;

import org.apache.commons.io.IOUtils;
import org.folio.okapi.common.ExtendedAsyncResult;
import org.folio.okapi.common.Failure;
import org.folio.okapi.common.Success;
import org.folio.rest.RestVerticle;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.Errors;
import org.folio.rest.jaxrs.model.Note;
import org.folio.rest.jaxrs.model.NoteCollection;
import org.folio.rest.jaxrs.model.UserDisplayInfo;
import org.folio.rest.jaxrs.resource.Notes;
import org.folio.rest.persist.Criteria.Criteria;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.Criteria.Limit;
import org.folio.rest.persist.Criteria.Offset;
import org.folio.rest.persist.PgExceptionUtil;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.cql.CQLWrapper;
import org.folio.rest.tools.client.HttpClientFactory;
import org.folio.rest.tools.client.interfaces.HttpClientInterface;
import org.folio.rest.tools.messages.MessageConsts;
import org.folio.rest.tools.messages.Messages;
import org.folio.rest.tools.utils.TenantTool;
import org.folio.rest.tools.utils.ValidationHelper;
import org.folio.type.NoteTypeRepository;
import org.z3950.zing.cql.cql2pgjson.CQL2PgJSON;
import org.z3950.zing.cql.cql2pgjson.FieldException;
import org.z3950.zing.cql.cql2pgjson.SchemaException;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;


@java.lang.SuppressWarnings({"squid:S1192"}) // This can be removed once John sets SQ up properly
public class NotesResourceImpl implements Notes {
  private final Logger logger = LoggerFactory.getLogger("mod-notes");
  private final Messages messages = Messages.getInstance();
  private static final String NOTE_TABLE = "note_data";
  private static final String LOCATION_PREFIX = "/notes/";
  private static final String IDFIELDNAME = "id";
  private String noteSchema = null;
  private static final String NOTE_SCHEMA_NAME = "ramls/note.json";
  private final NoteTypeRepository typeRepository;
  // Get this from the restVerticle, like the rest, when it gets defined there.

  private void initCQLValidation() {
    String path = NOTE_SCHEMA_NAME;
    try {
      noteSchema = IOUtils.toString(
        getClass().getClassLoader().getResourceAsStream(path), "UTF-8");
    } catch (Exception e) {
      logger.error("unable to load schema - " + path
        + ", validation of query fields will not be active");
    }
  }

  public NotesResourceImpl(Vertx vertx, String tenantId) {
    this(vertx, tenantId, new NoteTypeRepository());
  }

  public NotesResourceImpl(Vertx vertx, String tenantId, NoteTypeRepository typeRepository) {
    if (noteSchema == null) {
      initCQLValidation();
    }
    PostgresClient.getInstance(vertx, tenantId).setIdField(IDFIELDNAME);
    this.typeRepository = typeRepository;
  }

  private CQLWrapper getCQL(String query, int limit, int offset,
    String schema) throws IOException, FieldException, SchemaException  {
    CQL2PgJSON cql2pgJson;
    if (schema != null) {
      cql2pgJson = new CQL2PgJSON(NOTE_TABLE + ".jsonb", schema);
    } else {
      cql2pgJson = new CQL2PgJSON(NOTE_TABLE + ".jsonb");
    }
    return new CQLWrapper(cql2pgJson, query)
      .setLimit(new Limit(limit))
      .setOffset(new Offset(offset));
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

    logger.debug("Getting notes. new query:" + query);
    CQLWrapper cql;
    try {
      cql = getCQL(query, limit, offset, noteSchema);
    } catch (Exception e) {
      ValidationHelper.handleError(e, asyncResultHandler);
      return;
    }
    getNotes(vertxContext, tenantId, cql)
      .thenCompose(notes ->
        loadTypeNames(notes.getNotes(), okapiHeaders, vertxContext, tenantId)
          .thenAccept(o ->
            asyncResultHandler.handle(succeededFuture(GetNotesResponse.respond200WithApplicationJson(notes)))))
      .exceptionally(e -> {
        ValidationHelper.handleError(e.getCause(), asyncResultHandler);
        return null;
      });
  }

  private CompletableFuture<NoteCollection> getNotes(Context vertxContext, String tenantId, CQLWrapper cql) {
    CompletableFuture<NoteCollection> future = new CompletableFuture<>();
    PostgresClient.getInstance(vertxContext.owner(), tenantId)
      .get(NOTE_TABLE, Note.class, new String[]{"*"}, cql,
        true /*get count too*/, false /* set id */,
        reply -> {
          if (reply.succeeded()) {
            NoteCollection notes = new NoteCollection();
            @SuppressWarnings("unchecked")
            List<Note> notelist = reply.result().getResults();
            notes.setNotes(notelist);
            Integer totalRecords = reply.result().getResultInfo().getTotalRecords();
            notes.setTotalRecords(totalRecords);
            future.complete(notes);
          } else {
            future.completeExceptionally(reply.cause());
          }
        });
    return future;
  }

  /**
   * Post a note to the system.
   *
   */
  @Override
  @Validate
  public void postNotes(String lang,
    Note note, Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context context) {

    String tenantId = TenantTool.calculateTenantId(
      okapiHeaders.get(RestVerticle.OKAPI_HEADER_TENANT));
    String idt = note.getId();
    if (idt == null || idt.isEmpty()) {
      note.setId(UUID.randomUUID().toString());
    }
    // Get the creator names, if not there
    if (note.getCreator() == null
      || note.getCreator().getLastName() == null) {
      pn2LookupUser(okapiHeaders, tenantId, note, lang, res -> {
        if (res.succeeded()) {
          // we have a result already, pass it on
          if (res.result() != null) { // we have a result already, pass it on
            asyncResultHandler.handle(res);
          } else { // null indicates successfull lookup
            pn5InsertNote(context, tenantId, note, asyncResultHandler);
          }
        } else { // should not happen
          asyncResultHandler.handle(
            succeededFuture(PostNotesResponse.respond500WithTextPlain(
                messages.getMessage(lang, MessageConsts.InternalServerError))));
        }
      });
    }
    else { // no need to look anything up, proceed to actual post
      pn5InsertNote(context, tenantId, note, asyncResultHandler);
    }
  }

  // Post notes, part 2: Look up the user (skipped if we already have what we need)
  private void pn2LookupUser(Map<String, String> okapiHeaders,
    String tenantId, Note note, String lang,
    Handler<AsyncResult<Response>> asyncResultHandler) {

    String userId = okapiHeaders.get(RestVerticle.OKAPI_USERID_HEADER);
    if (userId == null) {
      logger.error("No userid header");
      asyncResultHandler.handle(succeededFuture(PostNotesResponse
        .respond400WithTextPlain("No " + RestVerticle.OKAPI_USERID_HEADER
          + ". Can not look up user")));
      return;
    }
    String okapiURL = okapiHeaders.get("X-Okapi-Url");
    HttpClientInterface client = HttpClientFactory.getHttpClient(okapiURL, tenantId);
    String url = "/users/" + userId;
    try {
      logger.debug("Looking up user " + url);
      CompletableFuture<org.folio.rest.tools.client.Response> response
        = client.request(url, okapiHeaders);
      response.whenComplete((resp, ex) ->
        pn3HandleLookupUserResponse(resp, note, asyncResultHandler, userId, lang)
      );
    } catch (Exception e) {
      logger.error(e.getMessage(), e);
      asyncResultHandler.handle(
        succeededFuture(PostNotesResponse.respond500WithTextPlain(
            messages.getMessage(lang, MessageConsts.InternalServerError))));
    }
  }

  // Post notes, part 3: Handle the user lookup response
  private void pn3HandleLookupUserResponse(org.folio.rest.tools.client.Response resp,
    Note note, Handler<AsyncResult<Response>> asyncResultHandler,
    String userId, String lang) {

    switch (resp.getCode()) {
      case 200:
        logger.debug("Received user " + resp.getBody());
        JsonObject usr = resp.getBody();
        if (usr.containsKey("username")
          && usr.containsKey("personal")) {
          if (note.getMetadata().getCreatedByUsername() == null) {
            note.getMetadata().setCreatedByUsername(usr.getString("username"));
          }
          if (note.getCreator()== null) {
            JsonObject p = usr.getJsonObject("personal");
            if (p != null) {
              UserDisplayInfo creator = new UserDisplayInfo();
              creator.setFirstName(p.getString("firstName"));
              creator.setMiddleName(p.getString("middleName"));
              creator.setLastName(p.getString("lastName"));
              note.setCreator(creator);
            }
          }
          // null indicates all is well, and we can proceed
          asyncResultHandler.handle(succeededFuture(null));
        } else {
          logger.error("User lookup failed for " + userId + ". Missing fields");
          logger.error(Json.encodePrettily(resp));
          asyncResultHandler.handle(succeededFuture(PostNotesResponse
            .respond400WithTextPlain("User lookup failed. "
              + "Missing fields in " + usr)));
        }
        break;
      case 404:
        logger.error("User lookup failed for " + userId);
        logger.error(Json.encodePrettily(resp));
        asyncResultHandler.handle(succeededFuture(PostNotesResponse
          .respond400WithTextPlain("User lookup failed. "
            + "Can not find user " + userId)));
        break;
      case 403:
        logger.error("User lookup failed for " + userId);
        logger.error(Json.encodePrettily(resp));
        asyncResultHandler.handle(succeededFuture(PostNotesResponse
          .respond400WithTextPlain("User lookup failed with 403. " + userId
            + " " + Json.encode(resp.getError()))));
        break;
      default:
        logger.error("User lookup failed with " + resp.getCode());
        logger.error(Json.encodePrettily(resp));
        asyncResultHandler.handle(
          succeededFuture(PostNotesResponse.respond500WithTextPlain(
            messages.getMessage(lang, MessageConsts.InternalServerError))));
        break;
    }
  }

  // Post notes part 5: Actually insert the note in the database
  private void pn5InsertNote(Context context, String tenantId, Note note,
    Handler<AsyncResult<Response>> asyncResultHandler) {

    String id = note.getId();
    PostgresClient.getInstance(context.owner(), tenantId)
      .save(NOTE_TABLE, id, note, reply -> {
        if (reply.succeeded()) {
          String ret = reply.result();
          note.setId(ret);
          asyncResultHandler.handle(succeededFuture(PostNotesResponse
            .respond201WithApplicationJson(note,
              PostNotesResponse.headersFor201().withLocation(LOCATION_PREFIX + ret))));
        } else {
          ValidationHelper.handleError(reply.cause(), asyncResultHandler);
        }
      });
  }



  /**
   * Helper to get a note and check permissions. Fetches the record from the
   * database, and verifies that the user has permissions to access its domain.
   *
   * @param id
   * @param okapiHeaders
   * @param resp a callback that returns the note, or an error
   */
  private void getOneNote(String id, Map<String, String> okapiHeaders,
    Context context, Handler<ExtendedAsyncResult<Note>> resp) {

    String tenantId = TenantTool.calculateTenantId(
      okapiHeaders.get(RestVerticle.OKAPI_HEADER_TENANT));
    Criterion c = new Criterion(
      new Criteria().addField(IDFIELDNAME).setJSONB(false)
        .setOperation("=").setValue("'" + id + "'"));
    PostgresClient.getInstance(context.owner(), tenantId)
      .get(NOTE_TABLE, Note.class, c, true,
        reply -> {
          if (reply.succeeded()) {
            @SuppressWarnings("unchecked")
            List<Note> notes = reply.result().getResults();
            if (notes.isEmpty()) {
              resp.handle(new Failure<>(NOT_FOUND, "Note " + id + " not found"));
            } else {
              resp.handle(new Success<>(notes.get(0)));
            }
          } else {
            String error = PgExceptionUtil.badRequestMessage(reply.cause());
            logger.error(error, reply.cause());
            if (error == null) {
              resp.handle(new Failure<>(INTERNAL, ""));
            } else {
              resp.handle(new Failure<>(USER, error));
            }
          }
        });
  }

  @Override
  @Validate
  public void getNotesById(String id,
                           String lang, Map<String, String> okapiHeaders,
                           Handler<AsyncResult<Response>> asyncResultHandler,
                           Context context) {
    String tenantId = TenantTool.calculateTenantId(
      okapiHeaders.get(RestVerticle.OKAPI_HEADER_TENANT));
    Handler<AsyncResult<Response>> handlerWrapper = result -> {
      if (isResponseOk(result)) {
        final Note note = (Note) result.result().getEntity();
        loadTypeNames(Collections.singletonList(note), okapiHeaders, context, tenantId)
          .thenAccept(o -> asyncResultHandler.handle(result));
      }else{
        asyncResultHandler.handle(result);
      }
    };

    PgUtil.getById(NOTE_TABLE, Note.class, id, okapiHeaders, context, GetNotesByIdResponse.class, handlerWrapper);
  }

  @Override
  @Validate
  public void deleteNotesById(String id,
    String lang, Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {
    String tenantId = TenantTool.calculateTenantId(
      okapiHeaders.get(RestVerticle.OKAPI_HEADER_TENANT));
    getOneNote(id, okapiHeaders, vertxContext, res -> {
      if (res.failed()) {
        switch (res.getType()) {
          // ValidationHelper can not handle these error types
          case NOT_FOUND:
            asyncResultHandler.handle(succeededFuture(DeleteNotesByIdResponse
              .respond404WithTextPlain(res.cause().getMessage())));
            break;
          case USER: // bad request
            asyncResultHandler.handle(succeededFuture(DeleteNotesByIdResponse
              .respond400WithTextPlain(res.cause().getMessage())));
            break;
          case FORBIDDEN:
            asyncResultHandler.handle(succeededFuture(DeleteNotesByIdResponse
              .respond401WithTextPlain(res.cause().getMessage())));
            break;
          default: // typically INTERNAL
            String msg = res.cause().getMessage();
            ValidationHelper.handleError(res.cause(), asyncResultHandler);
            break;
        }
      } else {    // all well, try to delete it
        deleteNotesById2(id, tenantId, lang,
          vertxContext, asyncResultHandler);
      }
    }); // getOneNote
  }

  private void deleteNotesById2(String id, String tenantId, String lang,
    Context vertxContext, Handler<AsyncResult<Response>> asyncResultHandler) {
    PostgresClient.getInstance(vertxContext.owner(), tenantId)
      .delete(NOTE_TABLE, id, reply -> {
        if (reply.succeeded()) {
          if (reply.result().getUpdated() == 1) {
            asyncResultHandler.handle(succeededFuture(
              DeleteNotesByIdResponse.respond204()));
          } else {
            logger.error(messages.getMessage(lang,
              MessageConsts.DeletedCountError, 1, reply.result().getUpdated()));
            asyncResultHandler.handle(succeededFuture(DeleteNotesByIdResponse
              .respond404WithTextPlain(messages.getMessage(lang,
                MessageConsts.DeletedCountError, 1, reply.result().getUpdated()))));
          }
        } else {
          ValidationHelper.handleError(reply.cause(), asyncResultHandler);
        }
      });
  }


  @Override
  @Validate
  public void putNotesById(String id, String lang, Note note,
    Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {
    logger.debug("PUT note " + id + " " + Json.encode(note));
    if (note.getId() == null) {
      note.setId(id);
      logger.debug("No Id in the note, taking the one from the link");
      // The RMB should handle this. See RMB-94
    }
    if (!note.getId().equals(id)) {
      Errors valErr = ValidationHelper.createValidationErrorMessage("id", note.getId(),
        "Can not change Id");
      asyncResultHandler.handle(succeededFuture(PutNotesByIdResponse
        .respond422WithApplicationJson(valErr)));
      return;
    }

    getOneNote(id, okapiHeaders, vertxContext, res -> {
      if (res.failed()) {
        switch (res.getType()) {
          case NOT_FOUND:
            asyncResultHandler.handle(succeededFuture(PutNotesByIdResponse
              .respond404WithTextPlain(res.cause().getMessage())));
            break;
          case USER: // bad request
            asyncResultHandler.handle(succeededFuture(PutNotesByIdResponse
              .respond400WithTextPlain(res.cause().getMessage())));
            break;
          case FORBIDDEN:
            asyncResultHandler.handle(succeededFuture(PutNotesByIdResponse
              .respond401WithTextPlain(res.cause().getMessage())));
            break;
          default: // typically INTERNAL
            String msg = res.cause().getMessage();
            if (msg.isEmpty()) {
              msg = messages.getMessage(lang, MessageConsts.InternalServerError);
            }
            asyncResultHandler.handle(succeededFuture(PutNotesByIdResponse
              .respond500WithTextPlain(msg)));
            break;
        }
      } else { // found the note. put it in the db
        // Copy readonly fields over (RMB removed them from the incoming note)
        Note oldNote = res.result();
        UserDisplayInfo creator = new UserDisplayInfo();
        creator.setFirstName(oldNote.getCreator().getFirstName());
        creator.setMiddleName(oldNote.getCreator().getMiddleName());
        creator.setLastName(oldNote.getCreator().getLastName());

        note.setCreator(creator);
        putNotesById3Update(id, lang, note,
          okapiHeaders, vertxContext, asyncResultHandler);
      }
    });
  }

  private void putNotesById3Update(String id, String lang, Note entity,
    Map<String, String> okapiHeaders, Context vertxContext,
    Handler<AsyncResult<Response>> asyncResultHandler) {

    String tenantId = TenantTool.calculateTenantId(
      okapiHeaders.get(RestVerticle.OKAPI_HEADER_TENANT));
    PostgresClient.getInstance(vertxContext.owner(), tenantId)
      .update(NOTE_TABLE, entity, id, reply -> {
        if (reply.succeeded()) {
          if (reply.result().getUpdated() == 0) {
            asyncResultHandler.handle(succeededFuture(
              PutNotesByIdResponse.respond500WithTextPlain(
                messages.getMessage(lang, MessageConsts.NoRecordsUpdated))));
          } else {
            asyncResultHandler.handle(succeededFuture(
              PutNotesByIdResponse.respond204()));
          }
        } else {
          ValidationHelper.handleError(reply.cause(), asyncResultHandler);
        }
      });
  }

  private CompletableFuture<Void> loadTypeNames(List<Note> noteList, Map<String, String> okapiHeaders, Context context, String tenantId) {
    List<String> typeIds = noteList.stream().map(Note::getTypeId).collect(Collectors.toList());
    return typeRepository.getTypesByIds(typeIds, okapiHeaders, context, tenantId)
      .thenAccept(
        typeNames -> noteList.forEach(
          note -> note.setType(typeNames.get(note.getTypeId()))));
  }

  private boolean isResponseOk(AsyncResult<Response> result) {
    return result.succeeded() && Response.Status.OK.getStatusCode() == result.result().getStatus();
  }
}
