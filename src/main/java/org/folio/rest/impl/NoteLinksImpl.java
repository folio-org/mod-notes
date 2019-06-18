package org.folio.rest.impl;

import static io.vertx.core.Future.succeededFuture;
import static org.apache.commons.lang3.EnumUtils.getEnumList;
import static org.apache.commons.lang3.EnumUtils.isValidEnum;
import static org.apache.commons.lang3.StringUtils.defaultString;
import static org.apache.commons.lang3.StringUtils.join;
import static org.apache.commons.lang3.Validate.isTrue;

import java.util.Map;
import java.util.function.Function;

import javax.ws.rs.core.Response;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import org.folio.common.pf.PartialFunction;
import org.folio.links.NoteLinksService;
import org.folio.model.EntityLink;
import org.folio.model.Order;
import org.folio.model.OrderBy;
import org.folio.model.RowPortion;
import org.folio.model.Status;
import org.folio.rest.RestVerticle;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.Link;
import org.folio.rest.jaxrs.model.NoteCollection;
import org.folio.rest.jaxrs.model.NoteLinksPut;
import org.folio.rest.jaxrs.resource.NoteLinks;
import org.folio.rest.tools.utils.TenantTool;
import org.folio.spring.SpringContextUtil;

public class NoteLinksImpl implements NoteLinks {

  @Autowired
  NoteLinksService noteLinksService;
  @Autowired
  @Qualifier("defaultExcHandler")
  private PartialFunction<Throwable, Response> exceptionHandler;

  public NoteLinksImpl() {
    SpringContextUtil.autowireDependencies(this, Vertx.currentContext());
  }

  @Override
  public void putNoteLinksTypeIdByTypeAndId(String type, String id, NoteLinksPut entity, Map<String, String> okapiHeaders,
                                            Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    if (entity.getNotes().isEmpty()) {
      handleSuccess(asyncResultHandler);
    }
    String tenantId = TenantTool.calculateTenantId(okapiHeaders.get(RestVerticle.OKAPI_HEADER_TENANT));
    Link link = new Link().withId(id).withType(type);
    noteLinksService.updateNoteLinks(entity, link, tenantId)
      .map(o -> {
        handleSuccess(asyncResultHandler);
        return null;
      }).otherwise(e -> {
      asyncResultHandler.handle(
        succeededFuture(PutNoteLinksTypeIdByTypeAndIdResponse.respond500WithTextPlain(e.getMessage())));
      return null;
    });
  }

  @Validate
  @Override
  public void getNoteLinksDomainTypeIdByDomainAndTypeAndId(String domain, String type, String id, String title,
                                                           String status, String orderBy, String order, int offset, int limit, Map<String, String> okapiHeaders,
                                                           Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    String tenantId = TenantTool.calculateTenantId(okapiHeaders.get(RestVerticle.OKAPI_HEADER_TENANT));

    try {
      validateParameters(status, order, orderBy);
    } catch (IllegalArgumentException e) {
      handleWithInvalidParameters(asyncResultHandler, e.getMessage());
    }

    Future<NoteCollection> notes = noteLinksService.findNotesByTitleAndStatus(new EntityLink(domain, type, id),
      title, Status.enumOf(status), OrderBy.enumOf(orderBy), Order.enumOf(order),
      new RowPortion(offset, limit), tenantId);

    respond(notes, GetNoteLinksDomainTypeIdByDomainAndTypeAndIdResponse::respond200WithApplicationJson,
      asyncResultHandler);
  }

  private void validateParameters(String status, String order, String orderBy) {
    validateEnum(Status.class, status);
    validateEnum(Order.class, order);
    validateEnum(OrderBy.class, orderBy);
  }

  private <E extends Enum<E>> void validateEnum(final Class<E> enumClass, final String enumName) {
    String normalized = defaultString(enumName).toUpperCase();

    isTrue(isValidEnum(enumClass, normalized),
      "%s is incorrect: %s. Possible values: %s",
      enumClass.getSimpleName(), enumName, join(getEnumList(enumClass), ", "));
  }

  private void handleSuccess(Handler<AsyncResult<Response>> asyncResultHandler) {
    asyncResultHandler.handle(succeededFuture(PutNoteLinksTypeIdByTypeAndIdResponse.respond204()));
  }

  private void handleWithInvalidParameters(Handler<AsyncResult<Response>> asyncResultHandler, String message) {
    asyncResultHandler.handle(succeededFuture(PutNoteLinksTypeIdByTypeAndIdResponse.respond400WithTextPlain(message)));
  }

  private <T> void respond(Future<T> result, Function<T, Response> mapper,
                           Handler<AsyncResult<Response>> asyncResultHandler) {
    result.map(mapper)
      .otherwise(exceptionHandler)
      .setHandler(asyncResultHandler);
  }
}
