package org.folio.rest.impl;

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
  private final String idFieldName = "id";
  private static String NOTE_SCHEMA = null;
  private static final String NOTE_SCHEMA_NAME
    = "apidocs/raml/note.json";

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
      //initCQLValidation();  // COmmented out, the validation fails a
        // prerfectly valid query=metaData.createdByUserId=e037b...
    }
    PostgresClient.getInstance(vertx, tenantId).setIdField(idFieldName);
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

  @Override
  public void getNotes(String query,
    int offset, int limit, String lang,
    Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context context) throws Exception {
    try {
      logger.info("Getting notes. " + offset + "+" + limit + " q=" + query);
      String tenantId = TenantTool.calculateTenantId(
        okapiHeaders.get(RestVerticle.OKAPI_HEADER_TENANT));
      CQLWrapper cql = getCQL(query, limit, offset, NOTE_SCHEMA);

      PostgresClient.getInstance(context.owner(), tenantId)
        .get(NOTE_TABLE, Note.class, new String[]{"*"}, cql, true, true,
          reply -> {
          try {
            if (reply.succeeded()) {
              NoteCollection notes = new NoteCollection();
              @SuppressWarnings("unchecked")
              List<Note> notelist = (List<Note>) reply.result()[0];
              notes.setNotes(notelist);
              notes.setTotalRecords((Integer) reply.result()[1]);
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
                logger.error(msg, reply.cause());
                asyncResultHandler.handle(succeededFuture(PostNotesResponse
                  .withPlainInternalServerError(
                    messages.getMessage(lang, MessageConsts.InternalServerError))));
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

  @Override
  public void getNotesById(String id,
    String lang, Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context context) throws Exception {
    try {
      String tenantId = TenantTool.calculateTenantId(
        okapiHeaders.get(RestVerticle.OKAPI_HEADER_TENANT));
      Criterion c = new Criterion(
        new Criteria().addField(idFieldName).setJSONB(false)
        .setOperation("=").setValue("'" + id + "'"));

      PostgresClient.getInstance(context.owner(), tenantId)
        .get(NOTE_TABLE, Note.class, c, true,
          reply -> {
          try {
            if (reply.succeeded()) {
              @SuppressWarnings("unchecked")
              List<Note> config = (List<Note>) reply.result()[0];
              if (config.isEmpty()) {
                asyncResultHandler.handle(succeededFuture(GetNotesByIdResponse
                    .withPlainNotFound(id)));
              } else {
                asyncResultHandler.handle(succeededFuture(GetNotesByIdResponse
                    .withJsonOK(config.get(0))));
              }
            } else {
              logger.error(reply.cause().getMessage(), reply.cause());
              asyncResultHandler.handle(succeededFuture(GetNotesByIdResponse
                .withPlainInternalServerError(
                  messages.getMessage(lang, MessageConsts.InternalServerError)
                  + " " + reply.cause().getMessage())));
            }
          } catch (Exception e) {
            logger.error(e.getMessage(), e);
            asyncResultHandler.handle(succeededFuture(GetNotesByIdResponse
              .withPlainInternalServerError(
                messages.getMessage(lang, MessageConsts.InternalServerError))));
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
  public void deleteNotesById(String id,
    String lang, Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) throws Exception {
    String tenantId = TenantTool.calculateTenantId(
      okapiHeaders.get(RestVerticle.OKAPI_HEADER_TENANT));
    try {
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
            logger.error(reply.cause());
            asyncResultHandler.handle(succeededFuture(DeleteNotesByIdResponse
              .withPlainInternalServerError(
                messages.getMessage(lang, MessageConsts.InternalServerError))));
          }
        });
    } catch (Exception e) {
      logger.error(e.getMessage(), e);
      asyncResultHandler.handle(succeededFuture(DeleteNotesByIdResponse
        .withPlainInternalServerError(
          messages.getMessage(lang, MessageConsts.InternalServerError))));
    }
  }

  @Override
  public void putNotesById(String id, String lang, Note entity,
    Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) throws Exception {
    try {
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

}
