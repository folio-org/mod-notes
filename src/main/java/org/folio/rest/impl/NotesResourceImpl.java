/*
 * Copyright (c) 2015-2017, Index Data
 * All rights reserved.
 * See the file LICENSE for details.
 */
package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.json.Json;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import java.util.Map;
import javax.ws.rs.core.Response;
import org.folio.rest.RestVerticle;
import org.folio.rest.jaxrs.model.Note;
import org.folio.rest.jaxrs.resource.NotesResource;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.tools.messages.MessageConsts;
import org.folio.rest.tools.utils.OutStream;
import org.folio.rest.tools.utils.TenantTool;

public class NotesResourceImpl implements NotesResource {
  private final Logger logger = LoggerFactory.getLogger("okapi");

  @Override
  public void getNotes(String lang, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) throws Exception {
    throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
  }

  @Override
  public void postNotes(String lang,
    Note entity, Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context context) throws Exception {
    try {
      String tenantId = TenantTool.calculateTenantId(okapiHeaders.get(RestVerticle.OKAPI_HEADER_TENANT));
      logger.info("Trying to post a note for '" + tenantId + "' " + Json.encode(entity));
      //PostgresClient.getInstance(context.owner(), tenantId).
      /*
       PostgresClient.getInstance(context.owner(), tenantId).save(
       CONFIG_TABLE,
       entity,
       reply -> {
       try {
       if(reply.succeeded()){
       Object ret = reply.result();
       entity.setId((String) ret);
       OutStream stream = new OutStream();
       stream.setData(entity);
       asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PostConfigurationsEntriesResponse.withJsonCreated(
       LOCATION_PREFIX + ret, stream)));
       }
       else{
       log.error(reply.cause().getMessage(), reply.cause());
       asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PostConfigurationsEntriesResponse
       .withPlainInternalServerError(messages.getMessage(lang, MessageConsts.InternalServerError))));
       }
       } catch (Exception e) {
       log.error(e.getMessage(), e);
       asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PostConfigurationsEntriesResponse
       .withPlainInternalServerError(messages.getMessage(lang, MessageConsts.InternalServerError))));
       }
       });
       */
    } catch (Exception e) {
      logger.error(e.getMessage(), e);
      /*
       asyncResultHandler.handle(
         io.vertx.core.Future.succeededFuture(PostConfigurationsEntriesResponse
          .withPlainInternalServerError(messages.getMessage(lang, MessageConsts.InternalServerError))));
         */
    }
    throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
  }
}
