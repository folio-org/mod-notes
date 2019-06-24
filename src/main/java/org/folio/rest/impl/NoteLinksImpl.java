package org.folio.rest.impl;

import static org.folio.rest.ResponseHelper.respond;
import static org.folio.rest.tools.utils.TenantTool.tenantId;
import static org.folio.rest.validate.ValidationMethods.validateEnum;

import java.util.Map;

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
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.Link;
import org.folio.rest.jaxrs.model.NoteCollection;
import org.folio.rest.jaxrs.model.NoteLinksPut;
import org.folio.rest.jaxrs.resource.NoteLinks;
import org.folio.rest.validate.Validation;
import org.folio.spring.SpringContextUtil;

public class NoteLinksImpl implements NoteLinks {

  @Autowired
  NoteLinksService noteLinksService;
  @Autowired
  @Qualifier("noteLinksExcHandler")
  private PartialFunction<Throwable, Response> excHandler;

  
  public NoteLinksImpl() {
    SpringContextUtil.autowireDependencies(this, Vertx.currentContext());
  }

  @Override
  public void putNoteLinksTypeIdByTypeAndId(String type, String id, NoteLinksPut entity, Map<String, String> okapiHeaders,
                                            Handler<AsyncResult<Response>> asyncHandler, Context vertxContext) {
    Future<Void> updated;
    if (entity.getNotes().isEmpty()) {
      updated = Future.succeededFuture();
    } else {
      updated = noteLinksService.updateNoteLinks(entity, link(type, id), tenantId(okapiHeaders));
    }

    respond(updated, v -> PutNoteLinksTypeIdByTypeAndIdResponse.respond204(), asyncHandler, excHandler);
  }

  @Validate
  @Override
  public void getNoteLinksDomainTypeIdByDomainAndTypeAndId(String domain, String type, String id, String title,
      String status, String orderBy, String order, int offset, int limit, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncHandler, Context vertxContext) {

    Future<Void> validated = Validation.instance()
      .addTest(status, validateEnum(Status.class))
      .addTest(order, validateEnum(Order.class))
      .addTest(orderBy, validateEnum(OrderBy.class))
      .validate();

    Future<NoteCollection> notes = validated.compose(
        v -> noteLinksService.findNotesByTitleAndStatus(new EntityLink(domain, type, id), title,
              Status.enumOf(status), OrderBy.enumOf(orderBy), Order.enumOf(order),
              new RowPortion(offset, limit), tenantId(okapiHeaders)));

    respond(notes, GetNoteLinksDomainTypeIdByDomainAndTypeAndIdResponse::respond200WithApplicationJson,
      asyncHandler, excHandler);
  }

  private static Link link(String type, String id) {
    return new Link().withId(id).withType(type);
  }

}
