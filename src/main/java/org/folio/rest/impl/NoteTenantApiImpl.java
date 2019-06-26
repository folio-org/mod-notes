package org.folio.rest.impl;

import java.util.Date;
import java.util.Map;

import javax.ws.rs.core.Response;

import org.folio.common.OkapiParams;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.Metadata;
import org.folio.rest.jaxrs.model.NoteType;
import org.folio.rest.jaxrs.model.TenantAttributes;
import org.folio.spring.SpringContextUtil;
import org.folio.type.NoteTypeRepository;
import org.springframework.beans.factory.annotation.Autowired;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public class NoteTenantApiImpl extends TenantAPI {

  public static final String DEFAULT_NOTE_TYPE_NAME = "General note";
  private final Logger logger = LoggerFactory.getLogger(NoteTenantApiImpl.class);

  @Autowired
  private NoteTypeRepository typeRepository;

  public NoteTenantApiImpl() {
    SpringContextUtil.autowireDependencies(this, Vertx.currentContext());
  }

  @Validate
  @Override
  public void postTenant(TenantAttributes entity, Map<String, String> headers, Handler<AsyncResult<Response>> handlers,
                         Context context) {
    Future<Response> future = Future.future();
    super.postTenant(entity, headers, future, context);

    future.compose(response -> populateDefaultNoteType(headers).map(response))
    .setHandler(handlers);
  }

  private Future<Object> populateDefaultNoteType(Map<String, String> headers) {
    return Future.succeededFuture(null)
      .compose(o -> {
        String tenant = new OkapiParams(headers).getTenant();
        NoteType type = new NoteType()
          .withName(DEFAULT_NOTE_TYPE_NAME)
          .withMetadata(new Metadata()
            .withCreatedDate(new Date())
            .withUpdatedDate(new Date()));

        return typeRepository.count(tenant)
          .compose(count -> {
            if(count == 0){
              return typeRepository.save(type, tenant)
                .map(savedType -> null);
            }
            return Future.succeededFuture(null);
          });
      })
      .otherwise(e -> {
        logger.error("Failed to populate default note type", e);
        return null;
      });
  }
}
