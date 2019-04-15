package org.folio.rest.impl;

import static io.vertx.core.Future.succeededFuture;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import javax.ws.rs.core.Response;

import org.folio.db.model.NoteView;
import org.folio.rest.RestVerticle;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.Errors;
import org.folio.rest.jaxrs.model.Note;
import org.folio.rest.jaxrs.model.NoteCollection;
import org.folio.rest.jaxrs.model.UserDisplayInfo;
import org.folio.rest.jaxrs.resource.Notes;
import org.folio.rest.persist.PgExceptionUtil;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.Criteria.Limit;
import org.folio.rest.persist.Criteria.Offset;
import org.folio.rest.persist.cql.CQLWrapper;
import org.folio.rest.persist.interfaces.Results;
import org.folio.rest.tools.client.HttpClientFactory;
import org.folio.rest.tools.client.interfaces.HttpClientInterface;
import org.folio.rest.tools.messages.MessageConsts;
import org.folio.rest.tools.messages.Messages;
import org.folio.rest.tools.utils.TenantTool;
import org.folio.rest.tools.utils.ValidationHelper;
import org.folio.userlookup.UserLookUp;
import org.z3950.zing.cql.cql2pgjson.CQL2PgJSON;
import org.z3950.zing.cql.cql2pgjson.FieldException;
import org.z3950.zing.cql.cql2pgjson.SchemaException;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.handler.impl.HttpStatusException;


@java.lang.SuppressWarnings({"squid:S1192"}) // This can be removed once John sets SQ up properly
public class NotesResourceImpl implements Notes {
  private static final String NOTE_TABLE = "note_data";
  private static final String NOTE_VIEW = "note_view";
  private static final String LOCATION_PREFIX = "/notes/";
  private static final String IDFIELDNAME = "id";
  private final Logger logger = LoggerFactory.getLogger("mod-notes");
  private final Messages messages = Messages.getInstance();
  private String noteSchema = null;
  // Get this from the restVerticle, like the rest, when it gets defined there.

  public NotesResourceImpl(Vertx vertx, String tenantId) {
    PostgresClient.getInstance(vertx, tenantId).setIdField(IDFIELDNAME);
  }

  private CQLWrapper getCQLForNoteView(String query, int limit, int offset,
                                       String schema) throws IOException, FieldException, SchemaException  {
    CQL2PgJSON cql2pgJson;
    if (schema != null) {
      cql2pgJson = new CQL2PgJSON(NOTE_VIEW + ".jsonb", schema);
    } else {
      cql2pgJson = new CQL2PgJSON(NOTE_VIEW + ".jsonb");
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
      cql = getCQLForNoteView(query, limit, offset, noteSchema);
    } catch (Exception e) {
      ValidationHelper.handleError(e, asyncResultHandler);
      return;
    }
    getNotes(vertxContext, tenantId, cql)
      .map(notes -> {
        asyncResultHandler.handle(succeededFuture(GetNotesResponse.respond200WithApplicationJson(notes)));
        return null;
      })
      .otherwise(e -> {
        ValidationHelper.handleError(e, asyncResultHandler);
        return null;
      });
  }

  private Future<NoteCollection> getNotes(Context vertxContext, String tenantId, CQLWrapper cql) {
    Future<Results<NoteView>> future = Future.future();
    PostgresClient.getInstance(vertxContext.owner(), tenantId)
      .get(NOTE_VIEW, NoteView.class, new String[]{"*"}, cql,
        true /*get count too*/, false /* set id */,
        future.completer());

    return future
      .map(this::mapNoteResults);
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
      .withTitle(noteView.getTitle())
      .withContent(noteView.getContent())
      .withCreator(noteView.getCreator())
      .withUpdater(noteView.getUpdater())
      .withMetadata(noteView.getMetadata())
      .withLinks(noteView.getLinks());
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
  public void getNotesById(String id,
                           String lang, Map<String, String> okapiHeaders,
                           Handler<AsyncResult<Response>> asyncResultHandler,
                           Context context) {
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

      getOneNote(id, okapiHeaders, vertxContext)
        .compose(oldNote -> {
          setNoteCreator(oldNote, note);
          return setNoteUpdater(note, okapiHeaders);
        })
        .compose(voidObject -> updateNote(id, note, okapiHeaders, asyncResultHandler, vertxContext))
        .otherwise(exception -> {

          if (exception instanceof HttpStatusException) {

            final int cause =  ((HttpStatusException) exception).getStatusCode();

            if (Response.Status.NOT_FOUND.getStatusCode() == cause) {
              asyncResultHandler.handle(succeededFuture(PutNotesByIdResponse.respond404WithTextPlain(
                  ((HttpStatusException) exception).getPayload())));
            } else if (Response.Status.BAD_REQUEST.getStatusCode() == cause) {
              asyncResultHandler.handle(succeededFuture(PutNotesByIdResponse.respond400WithTextPlain(
                  ((HttpStatusException) exception).getPayload())));
            } else {
              asyncResultHandler.handle(succeededFuture(PutNotesByIdResponse.respond500WithTextPlain(
                exception.getMessage())));
            }
          } else {
            asyncResultHandler.handle(succeededFuture(PutNotesByIdResponse.respond500WithTextPlain(exception.getMessage())));
          }
          return null;
      });
  }

  private Future<Void> setNoteCreator(Note oldNote, Note note) {
    final UserDisplayInfo creator = getUserDisplayInfo(oldNote.getCreator().getFirstName(), oldNote.getCreator()
        .getMiddleName(), oldNote.getCreator().getLastName());
    note.setCreator(creator);
    return null;
  }

  private Future<Void> setNoteUpdater(Note note, Map<String, String> okapiHeaders) {
    return UserLookUp.getUserInfo(okapiHeaders)
      .map(userLookUp -> {
        final UserDisplayInfo userDisplayInfo = getUserDisplayInfo(userLookUp.getFirstName(), userLookUp.getMiddleName(), userLookUp.getLastName());
        note.setUpdater(userDisplayInfo);
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
