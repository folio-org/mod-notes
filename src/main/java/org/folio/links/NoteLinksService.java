package org.folio.links;

import io.vertx.core.Future;

import org.folio.rest.jaxrs.model.Link;
import org.folio.rest.jaxrs.model.NoteCollection;
import org.folio.rest.jaxrs.model.NoteLinksPut;
import org.folio.rest.model.Order;
import org.folio.rest.model.OrderBy;
import org.folio.rest.model.Status;

public interface NoteLinksService {

  Future<Void> putNoteLinkTypeIdToNote(NoteLinksPut entity, Link link, String tenantId);

  Future<NoteCollection> getNoteCollection(Status status, String tenantId, Order order,
                                           OrderBy orderBy, String domain, String title, Link link, int limit, int offset);
}
