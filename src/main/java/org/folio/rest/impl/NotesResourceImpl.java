package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.Json;
import static io.vertx.core.Future.succeededFuture;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.ws.rs.core.Response;
import org.apache.commons.io.IOUtils;
import static org.folio.okapi.common.ErrorType.*;
import org.folio.okapi.common.ExtendedAsyncResult;
import org.folio.okapi.common.Failure;
import org.folio.okapi.common.Success;
import org.folio.rest.RestVerticle;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.Errors;
import org.folio.rest.jaxrs.model.Note;
import org.folio.rest.jaxrs.model.NoteCollection;
import org.folio.rest.jaxrs.model.Notification;
import org.folio.rest.jaxrs.resource.NotesResource;
import org.folio.rest.persist.Criteria.Criteria;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.Criteria.Limit;
import org.folio.rest.persist.Criteria.Offset;
import org.folio.rest.persist.PgExceptionUtil;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.cql.CQLWrapper;
import org.folio.rest.tools.client.HttpClientFactory;
import org.folio.rest.tools.client.interfaces.HttpClientInterface;
import org.folio.rest.tools.messages.MessageConsts;
import org.folio.rest.tools.messages.Messages;
import org.folio.rest.tools.utils.OutStream;
import org.folio.rest.tools.utils.TenantTool;
import org.folio.rest.tools.utils.ValidationHelper;
import org.z3950.zing.cql.cql2pgjson.CQL2PgJSON;
import org.z3950.zing.cql.cql2pgjson.FieldException;
import org.z3950.zing.cql.cql2pgjson.SchemaException;


@java.lang.SuppressWarnings({"squid:S1192"}) // This can be removed once John sets SQ up properly
public class NotesResourceImpl implements NotesResource {
  private final Logger logger = LoggerFactory.getLogger("mod-notes");
  private final Messages messages = Messages.getInstance();
  public static final String NOTE_TABLE = "note_data";
  private static final String LOCATION_PREFIX = "/notes/";
  private static final String IDFIELDNAME = "id";
  private String NOTE_SCHEMA = null;
  private static final String NOTE_SCHEMA_NAME = "ramls/note.json";
  // Get this from the restVerticle, like the rest, when it gets defined there.

  private void initCQLValidation() {
    String path = NOTE_SCHEMA_NAME;
    try {
      NOTE_SCHEMA = IOUtils.toString(
        getClass().getClassLoader().getResourceAsStream(path), "UTF-8");
    } catch (Exception e) {
      logger.error("unable to load schema - " + path
        + ", validation of query fields will not be active");
    }
  }

  public NotesResourceImpl(Vertx vertx, String tenantId) {
    if (NOTE_SCHEMA == null) {
      initCQLValidation();
    }
    PostgresClient.getInstance(vertx, tenantId).setIdField(IDFIELDNAME);
  }

  private CQLWrapper getCQL(String query, int limit, int offset,
    String schema) throws IOException, FieldException, SchemaException  {
    CQL2PgJSON cql2pgJson = null;
    if (schema != null) {
      cql2pgJson = new CQL2PgJSON(NOTE_TABLE + ".jsonb", schema);
    } else {
      cql2pgJson = new CQL2PgJSON(NOTE_TABLE + ".jsonb");
    }
    return new CQLWrapper(cql2pgJson, query)
      .setLimit(new Limit(limit))
      .setOffset(new Offset(offset));
  }

  /**
   * Helper to check if the user has a given notes.domain permission. For domain
   * "things", checks that X-Okapi-Headers contains "notes.domain.things" or
   * "notes.domain.all"
   *
   * @param domain
   * @param okapiHeaders
   * @return
   */
  private boolean noteDomainPermission(String domain,
    Map<String, String> okapiHeaders) {
    String perms = okapiHeaders.get(RestVerticle.OKAPI_HEADER_PERMISSIONS);
    if (perms == null || perms.isEmpty()) {
      logger.error("No " + RestVerticle.OKAPI_HEADER_PERMISSIONS
        + " - check notes.domain.* permissions");
      return false;
    }
    return perms.contains("notes.domain." + domain)
      || perms.contains("notes.domain.all");
  }

  @Override
  @Validate
  public void getNotes(String query,
    int offset, int limit, String lang,
    Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {

    getNotesBoth(false, query, offset, limit,
      lang, okapiHeaders,
      asyncResultHandler, vertxContext);
  }

  @Override
  @Validate
  public void getNotesSelf(String query, int offset, int limit,
    String lang, Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {
    getNotesBoth(true, query, offset, limit,
      lang, okapiHeaders,
      asyncResultHandler, vertxContext);
  }

  /*
   * Combined handler for get _self and plain get (collection)
   */
  @java.lang.SuppressWarnings({"squid:S00107"}) // 8 parameters, I know
  private void getNotesBoth(boolean self, String query, int offset, int limit,
    String lang, Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {

    logger.info("Getting notes. self=" + self + " "        + offset + "+" + limit + " q=" + query);

    String tenantId = TenantTool.calculateTenantId(
      okapiHeaders.get(RestVerticle.OKAPI_HEADER_TENANT));
    if (self) {
      String userId = okapiHeaders.get(RestVerticle.OKAPI_USERID_HEADER);
      if (userId == null || userId.isEmpty()) {
        logger.error("No userId for getNotesSelf");
        asyncResultHandler.handle(succeededFuture(GetNotesResponse
          .withPlainBadRequest("No UserId")));
        return;
      }
      String userQuery = "metadata.createdByUserId=\"" + userId + "\"";
      if (query == null) {
        query = userQuery;
      } else {
        query = "(" + userQuery + ") and (" + query + ")";
      }
    }
    String perms = okapiHeaders.get(RestVerticle.OKAPI_HEADER_PERMISSIONS);
    if (perms == null || perms.isEmpty()) {
      logger.error("No " + RestVerticle.OKAPI_HEADER_PERMISSIONS
        + " - check notes.domain.* permissions");
      asyncResultHandler.handle(succeededFuture(GetNotesResponse
        .withPlainUnauthorized("No notes.domain.* permissions")));
      return;
    }
    boolean allseen = false;
    String delim = "";
    StringBuilder pqb = new StringBuilder();
    for (String p : perms.split(",")) {
      if (p.equals("notes.domain.all")) {
        allseen = true;
        break;
      }
      if (p.startsWith("notes.domain.")) {
        pqb.append(delim)
          .append("domain=")
          .append(p.replaceFirst("^notes\\.domain\\.", ""));
        delim = " OR ";
      }
    }
    String pq = pqb.toString();
    if (!allseen && !pq.isEmpty()) {
      if (query == null) {
        query = pq;
      } else {
        query = "(" + query + ") and ( " + pq + ")";
      }
    }

    logger.info("Getting notes. new query:" + query);
    CQLWrapper cql = null;
    try {
      cql = getCQL(query, limit, offset, NOTE_SCHEMA);
    } catch (Exception e) {
      logger.info("XXX getCQL exception ", e);
      ValidationHelper.handleError(e, asyncResultHandler);
      return;
    }
    PostgresClient.getInstance(vertxContext.owner(), tenantId)
      .get(NOTE_TABLE, Note.class, new String[]{"*"}, cql,
        true /*get count too*/, false /* set id */,
        reply -> {
          if (reply.succeeded()) {
            NoteCollection notes = new NoteCollection();
            @SuppressWarnings("unchecked")
            List<Note> notelist = (List<Note>) reply.result().getResults();
            notes.setNotes(notelist);
            Integer totalRecords = reply.result().getResultInfo().getTotalRecords();
            notes.setTotalRecords(totalRecords);
            asyncResultHandler.handle(succeededFuture(
              GetNotesResponse.withJsonOK(notes)));
          } else {
            logger.error("XXX pgclient.get error callback", reply.cause());
            ValidationHelper.handleError(reply.cause(), asyncResultHandler);
          }
        });
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
    String domain = note.getDomain();
    if (domain == null || domain.isEmpty()) {
      domain = note.getLink().replaceFirst("^/?([^/]+).*$", "$1");
      note.setDomain(domain);
      logger.warn("Note has no domain. "
        + "That is DEPRECATED and will stop working soon!"
        + " For now, defaulting to '" + domain + "'");
    }
    if (!noteDomainPermission(domain, okapiHeaders)) {
      logger.warn("postNotes: XXX-2 No permission notes.domain." + domain);
      asyncResultHandler.handle(succeededFuture(PostNotesResponse
        .withPlainUnauthorized("XXX-2 No permission notes.domain." + domain)));
      return;
    }
    // Get the creator names, if not there
    if (note.getCreatorUserName() == null
      || note.getCreatorLastName() == null) {
      pn2LookupUser(okapiHeaders, tenantId, note, lang, res -> {
        if (res.succeeded()) {
          if (res.result() != null) { // we have a result already, pass it on
            asyncResultHandler.handle(res);
          } else { // null indicates successfull lookup
            pn4SendNotifies(context, tenantId, note, okapiHeaders, asyncResultHandler, lang);
          }
        } else { // should not happen
          asyncResultHandler.handle(
            succeededFuture(PostNotesResponse.withPlainInternalServerError(
                messages.getMessage(lang, MessageConsts.InternalServerError))));
        }
      });
    } else { // no need to look anything up, proceed to actual post
      pn4SendNotifies(context, tenantId, note, okapiHeaders, asyncResultHandler, lang);
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
        .withPlainBadRequest("No " + RestVerticle.OKAPI_USERID_HEADER
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
        succeededFuture(PostNotesResponse.withPlainInternalServerError(
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
          if (note.getCreatorUserName() == null) {
            note.setCreatorUserName(usr.getString("username"));
          }
          if (note.getCreatorLastName() == null) {
            JsonObject p = usr.getJsonObject("personal");
            if (p != null) {
              note.setCreatorFirstName(p.getString("firstName"));
              note.setCreatorMiddleName(p.getString("middleName"));
              note.setCreatorLastName(p.getString("lastName"));
            }
          }
          // null indicates all is well, and we can proceed
          asyncResultHandler.handle(succeededFuture(null));
        } else {
          logger.error("User lookup failed for " + userId + ". Missing fields");
          logger.error(Json.encodePrettily(resp));
          asyncResultHandler.handle(succeededFuture(PostNotesResponse
            .withPlainBadRequest("User lookup failed. "
              + "Missing fields in " + usr)));
        }
        break;
      case 404:
        logger.error("User lookup failed for " + userId);
        logger.error(Json.encodePrettily(resp));
        asyncResultHandler.handle(succeededFuture(PostNotesResponse
          .withPlainBadRequest("User lookup failed. "
            + "Can not find user " + userId)));
        break;
      case 403:
        logger.error("User lookup failed for " + userId);
        logger.error(Json.encodePrettily(resp));
        asyncResultHandler.handle(succeededFuture(PostNotesResponse
          .withPlainBadRequest("User lookup failed with 403. " + userId
            + " " + Json.encode(resp.getError()))));
        break;
      default:
        logger.error("User lookup failed with " + resp.getCode());
        logger.error(Json.encodePrettily(resp));
        asyncResultHandler.handle(
          succeededFuture(PostNotesResponse.withPlainInternalServerError(
            messages.getMessage(lang, MessageConsts.InternalServerError))));
        break;
    }
  }


  // Post notes part 4: Send notifies to users mentioned in the note text
  private void pn4SendNotifies(Context context, String tenantId, Note note,Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler, String lang) {
    checkUserTags(note, okapiHeaders, res->{
      if (res.succeeded()) {
        pn5InsertNote(context, tenantId, note, asyncResultHandler, lang);
      } else { // all errors map down to internal errors. They have been logged
        asyncResultHandler.handle(
          succeededFuture(PostNotesResponse.withPlainInternalServerError(
              res.cause().getMessage())));
      }
    });
  }

  // Post notes part 5: Actually insert the note in the database
  private void pn5InsertNote(Context context, String tenantId, Note note,
    Handler<AsyncResult<Response>> asyncResultHandler, String lang) {

    String id = note.getId();
    PostgresClient.getInstance(context.owner(), tenantId)
      .save(NOTE_TABLE, id, note, reply -> {
        if (reply.succeeded()) {
          Object ret = reply.result();
          note.setId((String) ret);
          OutStream stream = new OutStream();
          stream.setData(note);
          asyncResultHandler.handle(succeededFuture(PostNotesResponse
            .withJsonCreated(LOCATION_PREFIX + ret, stream)));
        } else {
          ValidationHelper.handleError(reply.cause(), asyncResultHandler);
        }
      });
  }

    // Helper to go through the note text, find all @username tags, and
  // send notifies to the mentioned users.
  private void checkUserTags(Note note, Map<String, String> okapiHeaders,
          Handler<ExtendedAsyncResult<Void>> handler) {
    String tenantId = TenantTool.calculateTenantId(
      okapiHeaders.get(RestVerticle.OKAPI_HEADER_TENANT));
    String txt = note.getText();
    java.util.regex.Pattern p
      = Pattern.compile("(^|\\W)@(\\w+)", Pattern.UNICODE_CHARACTER_CLASS);
    Matcher m = p.matcher(txt);
    String okapiURL = okapiHeaders.get("X-Okapi-Url");
    HttpClientInterface client = HttpClientFactory.getHttpClient(okapiURL, tenantId);
    checkUserTagsR(note, client, okapiHeaders, m, handler);
  }

  private void checkUserTagsR(Note note, HttpClientInterface client ,
          Map<String, String> okapiHeaders,  Matcher m,
          Handler<ExtendedAsyncResult<Void>> handler) {
    if (!m.find()) {
      handler.handle(new Success<>());
      return;
    }
    String username = m.group(2).replace("@", "");
    String url = "/notify/_username/" + username;
    try {
      Notification notification = new Notification();
      String message = note.getCreatorUserName() + " mentioned you in a note "
              + " " + note.getId() + " about " + note.getLink();
      notification.setText(message);
      notification.setLink(note.getLink());
      CompletableFuture<org.folio.rest.tools.client.Response> response
        = client.request(HttpMethod.POST, notification, url, okapiHeaders);
      response.whenComplete((resp, ex) -> {
        switch (resp.getCode()) {
          case 201:
            logger.debug("Posted notify for " + username + " OK");
            // recurse on to the next tag
            checkUserTagsR(note, client, okapiHeaders, m, handler);
            break;
          case 404:
            logger.info("Notify post didn't find the user " + username + ". Skipping it");
            // recurse on to the next tag
            checkUserTagsR(note, client, okapiHeaders, m, handler);
            break;
          default:
            logger.error("Notify post for " + username + " failed with " + resp.getCode());
            logger.error(Json.encode(resp.getError()));
            handler.handle(new Failure<>(INTERNAL, "Notify post failed with " + resp.getCode()));
            break;
        }
      });
    } catch (Exception e) {
      logger.error(e.getMessage(), e);
      handler.handle(new Failure<>(INTERNAL,e.getMessage()));
    }
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
            List<Note> notes = (List<Note>) reply.result().getResults();
            if (notes.isEmpty()) {
              resp.handle(new Failure<>(NOT_FOUND, "Note " + id + " not found"));
            } else {  // Can not use validationHelper here
              Note n = notes.get(0);
              String domain = n.getDomain();
              if (noteDomainPermission(domain, okapiHeaders)) {
                resp.handle(new Success<>(n));
              } else {
                logger.warn("XXX-3 getOneNote: No permission notes.domain." + domain);
                resp.handle(new Failure<>(FORBIDDEN,
                  "XXX-3 No permission notes.domain." + domain));
              }
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
    if (id.equals("_self")) {
      // The _self endpoint has already handled this request
      return;
    }
    getOneNote(id, okapiHeaders, context, res -> {
      if (res.succeeded()) {
        asyncResultHandler.handle(succeededFuture(GetNotesByIdResponse
          .withJsonOK(res.result())));
      } else {
        switch (res.getType()) {
          case NOT_FOUND:
            asyncResultHandler.handle(succeededFuture(GetNotesByIdResponse
              .withPlainNotFound(res.cause().getMessage())));
            break;
          case USER: // bad request
            asyncResultHandler.handle(succeededFuture(GetNotesByIdResponse
              .withPlainBadRequest(res.cause().getMessage())));
            break;
          case FORBIDDEN:
            asyncResultHandler.handle(succeededFuture(GetNotesByIdResponse
              .withPlainUnauthorized(res.cause().getMessage())));
            break;
          default: // typically INTERNAL
            String msg = res.cause().getMessage();
            if (msg.isEmpty()) {
              msg = messages.getMessage(lang, MessageConsts.InternalServerError);
            }
            asyncResultHandler.handle(succeededFuture(GetNotesByIdResponse
              .withPlainInternalServerError(msg)));
            break;
        }
      }
    });
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
        logger.warn("XXX deleteNotesById failed: " + res.getType());
        switch (res.getType()) {
          // ValidationHelper can not handle these error types
          case NOT_FOUND:
            asyncResultHandler.handle(succeededFuture(DeleteNotesByIdResponse
              .withPlainNotFound(res.cause().getMessage())));
            break;
          case USER: // bad request
            asyncResultHandler.handle(succeededFuture(DeleteNotesByIdResponse
              .withPlainBadRequest(res.cause().getMessage())));
            break;
          case FORBIDDEN:
            asyncResultHandler.handle(succeededFuture(DeleteNotesByIdResponse
              .withPlainUnauthorized(res.cause().getMessage())));
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
              DeleteNotesByIdResponse.withNoContent()));
          } else {
            logger.error(messages.getMessage(lang,
              MessageConsts.DeletedCountError, 1, reply.result().getUpdated()));
            asyncResultHandler.handle(succeededFuture(DeleteNotesByIdResponse
              .withPlainNotFound(messages.getMessage(lang,
                MessageConsts.DeletedCountError, 1, reply.result().getUpdated()))));
          }
        } else {
          logger.warn("XXX deleteNotesById2 failed: ", reply.cause());
          ValidationHelper.handleError(reply.cause(), asyncResultHandler);
        }
      });
  }


  @Override
  @Validate
  public void putNotesById(String id, String lang, Note note,
    Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {
    logger.info("PUT note " + id + " " + Json.encode(note));
    if (note.getId() == null) {
      note.setId(id);
      logger.debug("No Id in the note, taking the one from the link");
      // The RMB should handle this. See RMB-94
    }
    if (!note.getId().equals(id)) {
      Errors valErr = ValidationHelper.createValidationErrorMessage("id", note.getId(),
        "Can not change Id");
      asyncResultHandler.handle(succeededFuture(PutNotesByIdResponse
        .withJsonUnprocessableEntity(valErr)));
      return;
    }

    // Check the perm for the domain we are about to set
    // getOneNote will check the perm for the domain as it is in the db
    String newDomain = note.getDomain();
    if (!noteDomainPermission(newDomain, okapiHeaders)) {
      logger.warn("XXX-X putNotesById: Missing permission for new domain " + newDomain);
      asyncResultHandler.handle(succeededFuture(PutNotesByIdResponse
        .withPlainUnauthorized("XXX-1 No permission notes.domain." + newDomain)));
      return;
    }
    getOneNote(id, okapiHeaders, vertxContext, res -> {
      if (res.failed()) {
        switch (res.getType()) {
          case NOT_FOUND:
            asyncResultHandler.handle(succeededFuture(PutNotesByIdResponse
              .withPlainNotFound(res.cause().getMessage())));
            break;
          case USER: // bad request
            asyncResultHandler.handle(succeededFuture(PutNotesByIdResponse
              .withPlainBadRequest(res.cause().getMessage())));
            break;
          case FORBIDDEN:
            logger.warn("XXX putNotesById: getOneNote returned " + res.getType() + "  " + res.cause().getMessage());
            asyncResultHandler.handle(succeededFuture(PutNotesByIdResponse
              .withPlainUnauthorized(res.cause().getMessage())));
            break;
          default: // typically INTERNAL
            String msg = res.cause().getMessage();
            if (msg.isEmpty()) {
              msg = messages.getMessage(lang, MessageConsts.InternalServerError);
            }
            asyncResultHandler.handle(succeededFuture(PutNotesByIdResponse
              .withPlainInternalServerError(msg)));
            break;
        }
      } else { // found the note. put it in the db
        // Copy readonly fields over (RMB removed them from the incoming note)
        Note oldNote = res.result();
        note.setCreatorUserName(oldNote.getCreatorUserName());
        note.setCreatorLastName(oldNote.getCreatorLastName());
        note.setCreatorFirstName(oldNote.getCreatorFirstName());
        note.setCreatorMiddleName(oldNote.getCreatorMiddleName());
        putNotesById2Notify(id, lang, note,
          okapiHeaders, vertxContext, asyncResultHandler);
      }
    });
  }

  private void putNotesById2Notify(String id, String lang, Note note,
    Map<String, String> okapiHeaders, Context context,
    Handler<AsyncResult<Response>> asyncResultHandler ) {
    checkUserTags(note, okapiHeaders, res->{
      if (res.succeeded()) {
        putNotesById3Update(id, lang, note, okapiHeaders, context, asyncResultHandler);
      } else { // all errors map down to internal errors. They have been logged
        logger.warn("XXX putNotesById2Notify: checkUserTags failed: " + res.cause().getMessage());
        asyncResultHandler.handle(
          succeededFuture(PostNotesResponse.withPlainInternalServerError(
              res.cause().getMessage())));
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
              PutNotesByIdResponse.withPlainInternalServerError(
                messages.getMessage(lang, MessageConsts.NoRecordsUpdated))));
          } else {
            asyncResultHandler.handle(succeededFuture(
              PutNotesByIdResponse.withNoContent()));
          }
        } else {
          logger.warn("XXX : putNotesById3Update update failed: " + reply.cause().getMessage());
          ValidationHelper.handleError(reply.cause(), asyncResultHandler);
        }
      });
  }


  /**
   * Post to _self is not supported. The RMB creates one anyway.
   *
   */
  @Override
  public void postNotesSelf(String lang, Note entity,
    Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) throws Exception {
    throw new UnsupportedOperationException("Not supported yet.");
  }


}
