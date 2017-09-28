package org.folio.rest.impl;

import io.netty.util.concurrent.FailedFuture;
import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.Json;
import static io.vertx.core.Future.succeededFuture;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import java.util.List;
import java.util.Map;
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
import org.folio.rest.jaxrs.resource.NotesResource;
import org.folio.rest.persist.Criteria.Criteria;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.Criteria.Limit;
import org.folio.rest.persist.Criteria.Offset;
import org.folio.rest.persist.PgExceptionUtil;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.cql.CQLQueryValidationException;
import org.folio.rest.persist.cql.CQLWrapper;
import org.folio.rest.tools.messages.MessageConsts;
import org.folio.rest.tools.messages.Messages;
import org.folio.rest.tools.utils.OutStream;
import org.folio.rest.tools.utils.TenantTool;
import org.folio.rest.tools.utils.ValidationHelper;
import org.z3950.zing.cql.cql2pgjson.CQL2PgJSON;

public class NotesResourceImpl implements NotesResource {
  private final Logger logger = LoggerFactory.getLogger("okapi");
  private final Messages messages = Messages.getInstance();
  public static final String NOTE_TABLE = "note_data";
  private static final String LOCATION_PREFIX = "/notes/";
  private static final String IDFIELDNAME = "id";
  private static String NOTE_SCHEMA = null;
  private static final String NOTE_SCHEMA_NAME = "apidocs/raml/note.json";
  private static final String OKAPI_PERM_HEADER = "X-Okapi-Permissions";
    // TODO - Get this from teh restVerticle, like the rest, when it gets defined there.

  private void initCQLValidation() {  //NOSONAR
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
      //initCQLValidation();  // NOSONAR
      // Commented out, because it fails a perfectly valid query
      // like metadata.createdDate=2017
    }
    PostgresClient.getInstance(vertx, tenantId).setIdField(IDFIELDNAME);
  }

  private CQLWrapper getCQL(String query, int limit, int offset,
    String schema) throws Exception {
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
   * "things", checks that X-Okapi-Headers contains "notes.domains.things" or
   * "notes.domains.all"
   *
   * @param domain
   * @param okapiHeaders
   * @return
   */
  private boolean noteDomainPermission(String domain,
    Map<String, String> okapiHeaders) {
    String perms = okapiHeaders.get(OKAPI_PERM_HEADER);
    if (perms == null || perms.isEmpty()) {
      logger.error("No " + OKAPI_PERM_HEADER + " - check notes.domain.* permissions");
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
    Context vertxContext) throws Exception {

    getNotesBoth(false, query, offset, limit,
      lang, okapiHeaders,
      asyncResultHandler, vertxContext);
  }

  @Override
  @Validate
  public void getNotesSelf(String query, int offset, int limit,
    String lang, Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) throws Exception {

    getNotesBoth(true, query, offset, limit,
      lang, okapiHeaders,
      asyncResultHandler, vertxContext);
  }

  /*
   * Combined handler for get _self and plain get (collection)
   */
  private void getNotesBoth(boolean self, String query, int offset, int limit,
    String lang, Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) throws Exception {
    try {
      logger.info("Getting notes. self=" + self + " "
        + offset + "+" + limit + " q=" + query);

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
      String perms = okapiHeaders.get(OKAPI_PERM_HEADER);
      if (perms == null || perms.isEmpty()) {
        logger.error("No " + OKAPI_PERM_HEADER + " - check notes.domain.* permissions");
        asyncResultHandler.handle(succeededFuture(GetNotesResponse
          .withPlainUnauthorized("No notes.domain.* permissions")));
        return;
      }
      if (!perms.contains("notes.domain.all")) {
        String p = perms.replaceAll("notes\\.domain\\.", "domain=");
        String[] pa = p.split(",");
        String pq = String.join(" OR ", pa);
        if (query == null) {
          query = pq;
        } else {
          query = "(" + query + ") and ( " + pq + ")";
        }
      }

      logger.info("Getting notes. new query:" + query);
      CQLWrapper cql = getCQL(query, limit, offset, NOTE_SCHEMA);

      PostgresClient.getInstance(vertxContext.owner(), tenantId)
        .get(NOTE_TABLE, Note.class, new String[]{"*"}, cql,
          true /*get count too*/, false /* set id */,
          reply -> {
            try {
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
                logger.error(reply.cause().getMessage(), reply.cause());
                asyncResultHandler.handle(succeededFuture(GetNotesResponse
                    .withPlainBadRequest(reply.cause().getMessage())));
              }
            } catch (Exception e) {
              logger.error(e.getMessage(), e);
              asyncResultHandler.handle(succeededFuture(GetNotesResponse
                  .withPlainInternalServerError(messages.getMessage(
                      lang, MessageConsts.InternalServerError))));
            }
          });
    } catch (CQLQueryValidationException e1) {
      int start = e1.getMessage().indexOf("'");
      int end = e1.getMessage().lastIndexOf("'");
      String field = e1.getMessage();
      if (start != -1 && end != -1) {
        field = field.substring(start + 1, end);
      }
      Errors e = ValidationHelper.createValidationErrorMessage(field,
        "", e1.getMessage());
      asyncResultHandler.handle(succeededFuture(GetNotesResponse
        .withJsonUnprocessableEntity(e)));
    } catch (Exception e) {
      logger.error(e.getMessage(), e);
      String message = messages.getMessage(lang, MessageConsts.InternalServerError);
      if (e.getCause() != null && e.getCause().getClass().getSimpleName()
        .endsWith("CQLParseException")) {
        message = " CQL parse error " + e.getLocalizedMessage();
      }
      asyncResultHandler.handle(succeededFuture(GetNotesResponse
        .withPlainInternalServerError(message)));
    }
  }

  /**
   * Post a note to the system.
   *
   * @param lang
   * @param entity
   * @param okapiHeaders
   * @param asyncResultHandler
   * @param context
   * @throws Exception
   */
  @Override
  @Validate
  public void postNotes(String lang,
    Note entity, Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context context) throws Exception {
    try {
      String tenantId = TenantTool.calculateTenantId(
        okapiHeaders.get(RestVerticle.OKAPI_HEADER_TENANT));
      String id = entity.getId();
      String domain = entity.getDomain();
      if (domain == null || domain.isEmpty()) {
        domain = entity.getLink().replaceFirst("^/?([^/]+).*$", "$1");
        entity.setDomain(domain);
        logger.warn("Note has no domain. "
          + "That is DEPRECATED and will stop working soon!"
          + " For now, defaulting to '" + domain + "'");
      }
      if (!noteDomainPermission(domain, okapiHeaders)) {
        asyncResultHandler.handle(succeededFuture(PostNotesResponse
          .withPlainUnauthorized("No permission notes.domain." + domain)));
        return;
      }
      PostgresClient.getInstance(context.owner(), tenantId).save(NOTE_TABLE,
        id, entity,
        reply -> {
          try {
            if (reply.succeeded()) {
              Object ret = reply.result();
              entity.setId((String) ret);
              OutStream stream = new OutStream();
              stream.setData(entity);
              asyncResultHandler.handle(succeededFuture(PostNotesResponse
                .withJsonCreated(LOCATION_PREFIX + ret, stream)));
            } else {
              String msg = reply.cause().getMessage();
              if (msg.contains("duplicate key value violates unique constraint")) {
                Errors valErr = ValidationHelper.createValidationErrorMessage(
                  "id", id, "Duplicate id");
                asyncResultHandler.handle(succeededFuture(PostNotesResponse
                  .withJsonUnprocessableEntity(valErr)));
              } else {
                String error = PgExceptionUtil.badRequestMessage(reply.cause());
                logger.error(msg, reply.cause());
                if (error == null) {
                  asyncResultHandler.handle(succeededFuture(PostNotesResponse
                    .withPlainInternalServerError(
                      messages.getMessage(lang, MessageConsts.InternalServerError))));
                } else {
                  asyncResultHandler.handle(succeededFuture(PostNotesResponse
                    .withPlainBadRequest(error)));
                }
              }
            }
          } catch (Exception e) {
            logger.error(e.getMessage(), e);
            asyncResultHandler.handle(succeededFuture(PostNotesResponse
              .withPlainInternalServerError(
                messages.getMessage(lang, MessageConsts.InternalServerError))));
          }
        });

    } catch (Exception e) {
      logger.error(e.getMessage(), e);
       asyncResultHandler.handle(
         succeededFuture(PostNotesResponse.withPlainInternalServerError(
             messages.getMessage(lang, MessageConsts.InternalServerError)))
      );
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
    try {

      String tenantId = TenantTool.calculateTenantId(
        okapiHeaders.get(RestVerticle.OKAPI_HEADER_TENANT));
      Criterion c = new Criterion(
        new Criteria().addField(IDFIELDNAME).setJSONB(false)
        .setOperation("=").setValue("'" + id + "'"));
      PostgresClient.getInstance(context.owner(), tenantId)
        .get(NOTE_TABLE, Note.class, c, true,
          reply -> {
            try {
              if (reply.succeeded()) {
                @SuppressWarnings("unchecked")
                List<Note> notes = (List<Note>) reply.result().getResults();
                if (notes.isEmpty()) {
                  resp.handle(new Failure<>(NOT_FOUND, "Note " + id + " not found"));
                } else {
                  Note n = notes.get(0);
                  String domain = n.getDomain();
                  if (noteDomainPermission(domain, okapiHeaders)) {
                    resp.handle(new Success<>(n));
                  } else {
                    resp.handle(new Failure<>(ANY,
                      "No permission notes.domain." + domain));
                  }  // TODO - Use FORBIDDEN once we have that defined
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
            } catch (Exception e) {
              logger.error(e.getMessage(), e);
              resp.handle(new Failure<>(INTERNAL, ""));
            }
          });
    } catch (Exception e) {
      logger.error(e.getMessage(), e);
      resp.handle(new Failure<>(INTERNAL, ""));
    }

  }

  @Override
  @Validate
  public void getNotesById(String id,
    String lang, Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context context) throws Exception {
    try {
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
              asyncResultHandler.handle(succeededFuture(PostNotesResponse
                .withPlainBadRequest(res.cause().getMessage())));
              break;
            case ANY: // means FORBIDDEN
              asyncResultHandler.handle(succeededFuture(GetNotesByIdResponse
                .withPlainUnauthorized(res.cause().getMessage())));
              break;
            default: // typically INTERNAL
              String msg = res.cause().getMessage();
              if (msg.isEmpty()) {
                msg = messages.getMessage(lang, MessageConsts.InternalServerError);
              }
              asyncResultHandler.handle(succeededFuture(PostNotesResponse
                .withPlainInternalServerError(msg)));
              break;
          }
        }
      });
    } catch (Exception e) {
      logger.error(e.getMessage(), e);
      asyncResultHandler.handle(succeededFuture(GetNotesByIdResponse
        .withPlainInternalServerError(
          messages.getMessage(lang, MessageConsts.InternalServerError))));
    }
  }

  @Override
  @Validate
  public void deleteNotesById(String id,
    String lang, Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) throws Exception {
    String tenantId = TenantTool.calculateTenantId(
      okapiHeaders.get(RestVerticle.OKAPI_HEADER_TENANT));
    getOneNote(id, okapiHeaders, vertxContext, res -> {
      if (res.failed()) {
        switch (res.getType()) {
          case NOT_FOUND:
            asyncResultHandler.handle(succeededFuture(GetNotesByIdResponse
              .withPlainNotFound(res.cause().getMessage())));
            break;
          case USER: // bad request
            asyncResultHandler.handle(succeededFuture(PostNotesResponse
              .withPlainBadRequest(res.cause().getMessage())));
            break;
          case ANY: // means FORBIDDEN
            asyncResultHandler.handle(succeededFuture(GetNotesByIdResponse
              .withPlainUnauthorized(res.cause().getMessage())));
            break;
          default: // typically INTERNAL
            String msg = res.cause().getMessage();
            if (msg.isEmpty()) {
              msg = messages.getMessage(lang, MessageConsts.InternalServerError);
            }
            asyncResultHandler.handle(succeededFuture(PostNotesResponse
              .withPlainInternalServerError(msg)));
            break;
        }
      } else { // all well, try to delete it
        try { // There is some duplicate error handling. Can't help.
          PostgresClient.getInstance(vertxContext.owner(), tenantId)
            .delete(NOTE_TABLE, id,
              reply -> {
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
                  String error = PgExceptionUtil.badRequestMessage(reply.cause());
                  logger.error(error, reply.cause());
                  if (error == null) {
                    asyncResultHandler.handle(succeededFuture(PostNotesResponse
                        .withPlainInternalServerError(
                          messages.getMessage(lang, MessageConsts.InternalServerError))));
                  } else {
                    asyncResultHandler.handle(succeededFuture(PostNotesResponse
                        .withPlainBadRequest(error)));
                  }
                }
              });
        } catch (Exception e) {
          logger.error(e.getMessage(), e);
          asyncResultHandler.handle(succeededFuture(DeleteNotesByIdResponse
            .withPlainInternalServerError(
              messages.getMessage(lang, MessageConsts.InternalServerError))));
        }
      }
    }); // getOneNote
  }


  @Override
  @Validate
  public void putNotesById(String id, String lang, Note entity,
    Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) throws Exception {
    logger.info("PUT note " + id + " " + Json.encode(entity));
    String noteId = entity.getId();
    if (noteId != null && !noteId.equals(id)) {
      logger.error("Trying to change note Id from " + id + " to " + noteId);
      Errors valErr = ValidationHelper.createValidationErrorMessage("id", noteId,
        "Can not change the id");
      asyncResultHandler.handle(succeededFuture(PutNotesByIdResponse
        .withJsonUnprocessableEntity(valErr)));
      return;
    }
    // Check the perm for the domain we are about to set
    // later we also check the perm for the domain as it is in the db
    String newDomain = entity.getDomain();
    if (!noteDomainPermission(newDomain, okapiHeaders)) {
      asyncResultHandler.handle(succeededFuture(PostNotesResponse
        .withPlainUnauthorized("No permission notes.domain." + newDomain)));
      return;
    }
    getOneNote(id, okapiHeaders, vertxContext, res -> {
      if (res.failed()) {
        switch (res.getType()) {
          case NOT_FOUND:
            asyncResultHandler.handle(succeededFuture(GetNotesByIdResponse
              .withPlainNotFound(res.cause().getMessage())));
            break;
          case USER: // bad request
            asyncResultHandler.handle(succeededFuture(PostNotesResponse
              .withPlainBadRequest(res.cause().getMessage())));
            break;
          case ANY: // means FORBIDDEN
            asyncResultHandler.handle(succeededFuture(GetNotesByIdResponse
              .withPlainUnauthorized(res.cause().getMessage())));
            break;
          default: // typically INTERNAL
            String msg = res.cause().getMessage();
            if (msg.isEmpty()) {
              msg = messages.getMessage(lang, MessageConsts.InternalServerError);
            }
            asyncResultHandler.handle(succeededFuture(PostNotesResponse
              .withPlainInternalServerError(msg)));
            break;
        }
      } else {
        try {
          String tenantId = TenantTool.calculateTenantId(
            okapiHeaders.get(RestVerticle.OKAPI_HEADER_TENANT));
          PostgresClient.getInstance(vertxContext.owner(), tenantId).update(
            NOTE_TABLE, entity, id,
            reply -> {
              try {
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
                  logger.error(reply.cause().getMessage());
                  asyncResultHandler.handle(succeededFuture(
                      PutNotesByIdResponse.withPlainInternalServerError(
                        messages.getMessage(lang, MessageConsts.InternalServerError))));
                }
              } catch (Exception e) {
                logger.error(e.getMessage(), e);
                asyncResultHandler.handle(succeededFuture(
                    PutNotesByIdResponse.withPlainInternalServerError(
                      messages.getMessage(lang, MessageConsts.InternalServerError))));
              }
            });
        } catch (Exception e) {
          logger.error(e.getMessage(), e);
          asyncResultHandler.handle(succeededFuture(PutNotesByIdResponse
            .withPlainInternalServerError(
              messages.getMessage(lang, MessageConsts.InternalServerError))));
        }
      }
    });
  }


  /**
   * Post to _self is not supported. The RMB creates one anyway.
   *
   * @throws Exception
   */
  @Override
  public void postNotesSelf(String lang, Note entity, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) throws Exception {
    throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
  }

}
