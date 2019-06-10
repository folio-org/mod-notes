package org.folio.rest.impl;

import static io.vertx.core.Future.succeededFuture;

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
import org.folio.rest.RestVerticle;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.Link;
import org.folio.rest.jaxrs.model.NoteCollection;
import org.folio.rest.jaxrs.model.NoteLinksPut;
import org.folio.rest.jaxrs.resource.NoteLinks;
import org.folio.rest.model.Order;
import org.folio.rest.model.OrderBy;
import org.folio.rest.model.Status;
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

    Link link = new Link().withId(id).withType(type);
    try {
      validateParameters(status, order, orderBy);
    } catch (IllegalArgumentException e) {
      handleWithInvalidParameters(asyncResultHandler, e.getMessage());
    }

    Status parsedStatus = Status.valueOf(status.toUpperCase());
    Order parsedOrder = Order.valueOf(order.toUpperCase());
    OrderBy parsedOrderBy = OrderBy.valueOf(orderBy.toUpperCase());

    Future<NoteCollection> notes = noteLinksService.getNoteCollection(parsedStatus, tenantId, parsedOrder,
      parsedOrderBy, domain, title, link, limit, offset);

    respond(notes, GetNoteLinksDomainTypeIdByDomainAndTypeAndIdResponse::respond200WithApplicationJson,
      asyncResultHandler);
  }

  private void validateParameters(String status, String order, String orderBy) throws IllegalArgumentException {
    if (!Status.contains(status.toUpperCase())) {
      throw new IllegalArgumentException("Status is incorrect. Possible values: \"ASSIGNED\",\"UNASSIGNED\",\"ALL\"");
    }
    if (!Order.contains(order.toUpperCase())) {
      throw new IllegalArgumentException("Order is incorrect. Possible values: \"asc\",\"desc\"");
    }
    if (!OrderBy.contains(orderBy.toUpperCase())) {
      throw new IllegalArgumentException("OrderBy is incorrect. Possible values: \"status\",\"title\"");
    }
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
