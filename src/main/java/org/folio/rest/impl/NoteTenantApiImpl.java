package org.folio.rest.impl;

import java.util.Date;
import java.util.Map;

import javax.ws.rs.core.Response;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import org.folio.common.OkapiParams;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.Metadata;
import org.folio.rest.jaxrs.model.NoteType;
import org.folio.rest.jaxrs.model.TenantAttributes;
import org.folio.spring.SpringContextUtil;
import org.folio.type.NoteTypeRepository;

public class NoteTenantApiImpl extends TenantAPI {

  private final Logger logger = LogManager.getLogger(NoteTenantApiImpl.class);

  @Value("${note.types.default.name}")
  private String defaultNoteTypeName;
  @Autowired
  private NoteTypeRepository typeRepository;

  public NoteTenantApiImpl() {
    SpringContextUtil.autowireDependencies(this, Vertx.currentContext());
  }

  @Validate
  @Override
  public void postTenant(TenantAttributes entity, Map<String, String> headers, Handler<AsyncResult<Response>> handlers,
                         Context context) {
    Promise<Response> promise = Promise.promise();
    super.postTenant(entity, headers, promise, context);

    promise.future().compose(response -> populateDefaultNoteType(headers).map(response))
      .onComplete(handlers);
  }

  private Future<Object> populateDefaultNoteType(Map<String, String> headers) {
    return Future.succeededFuture(null)
      .compose(o -> {
        String tenant = new OkapiParams(headers).getTenant();
        NoteType type = new NoteType()
          .withName(defaultNoteTypeName)
          .withMetadata(new Metadata()
            .withCreatedDate(new Date())
            .withUpdatedDate(new Date()));

        return typeRepository.count(tenant)
          .compose(count -> {
            if (count == 0) {
              return typeRepository.save(type, tenant)
                .map(savedType -> {
                  logger.info("Added default note type '{}'", savedType.getName());
                  return null;
                });
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
