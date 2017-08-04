/*
 * Copyright (c) 2015-2017, Index Data
 * All rights reserved.
 * See the file LICENSE for details.
 */
package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.Json;
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
import org.folio.rest.jaxrs.model.NoteCollectionJson;
import org.folio.rest.jaxrs.resource.NotesResource;
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
    = "apidocs/raml/_schemas/note.schema";

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
    PostgresClient.getInstance(vertx, tenantId).setIdField(idFieldName);
  }

  @Override
  public void getNotes(String query,
    int offset, int limit, String lang,
    Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context context) throws Exception {
    try {
      logger.info("Getting notes. " + offset + "+" + limit + " q=" + query);
      String tenantId = TenantTool.calculateTenantId(okapiHeaders.get(RestVerticle.OKAPI_HEADER_TENANT));
      CQLWrapper cql = getCQL(query, limit, offset, NOTE_SCHEMA);

      PostgresClient.getInstance(context.owner(), tenantId).get(NOTE_TABLE, Note.class,
        new String[]{"*"}, cql, true, true,
        reply -> {
          try {
            if (reply.succeeded()) {
              NoteCollectionJson notes = new NoteCollectionJson();
              @SuppressWarnings("unchecked")
              List<Note> notelist = (List<Note>) reply.result()[0];
              notes.setNotes(notelist);
              notes.setTotalRecords((Integer) reply.result()[1]);
              asyncResultHandler.handle(
                io.vertx.core.Future.succeededFuture(
                  GetNotesResponse.withJsonOK(notes)));
            } else {
              logger.error(reply.cause().getMessage(), reply.cause());
              asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetNotesResponse                  .withPlainBadRequest(reply.cause().getMessage())));
            }
          } catch (Exception e) {
            logger.error(e.getMessage(), e);
            asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetNotesResponse                .withPlainInternalServerError(messages.getMessage(
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
      Errors e = ValidationHelper.createValidationErrorMessage(field, "", e1.getMessage());
      asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetNotesResponse
        .withJsonUnprocessableEntity(e)));
    } catch (Exception e) {
      logger.error(e.getMessage(), e);
      String message = messages.getMessage(lang, MessageConsts.InternalServerError);
      if (e.getCause() != null && e.getCause().getClass().getSimpleName().endsWith("CQLParseException")) {
        message = " CQL parse error " + e.getLocalizedMessage();
      }
      asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetNotesResponse
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
      String tenantId = TenantTool.calculateTenantId(okapiHeaders.get(RestVerticle.OKAPI_HEADER_TENANT));
      logger.info("Trying to post a note for '" + tenantId + "' " + Json.encode(entity));
      PostgresClient.getInstance(context.owner(), tenantId).save(NOTE_TABLE,
        entity,
        reply -> {
          try {
            if (reply.succeeded()) {
              Object ret = reply.result();
              entity.setId((String) ret);
              OutStream stream = new OutStream();
              stream.setData(entity);
              asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PostNotesResponse.withJsonCreated(                    LOCATION_PREFIX + ret, stream)));
            } else {
              logger.error(reply.cause().getMessage(), reply.cause());
              asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PostNotesResponse                  .withPlainInternalServerError(messages.getMessage(lang, MessageConsts.InternalServerError))));
            }
          } catch (Exception e) {
            logger.error(e.getMessage(), e);
            asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PostNotesResponse                .withPlainInternalServerError(messages.getMessage(lang, MessageConsts.InternalServerError))));
          }
        });

    } catch (Exception e) {
      logger.error(e.getMessage(), e);
       asyncResultHandler.handle(
        io.vertx.core.Future.succeededFuture(
          PostNotesResponse.withPlainInternalServerError(
            messages.getMessage(lang, MessageConsts.InternalServerError)))
      );
    }
  }

  private CQLWrapper getCQL(String query, int limit, int offset, String schema) throws Exception {
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

}
