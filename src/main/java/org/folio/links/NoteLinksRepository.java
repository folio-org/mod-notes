package org.folio.links;

import java.util.List;

import io.vertx.core.Future;

import org.folio.rest.jaxrs.model.Link;
import org.folio.rest.jaxrs.model.NoteCollection;
import org.folio.rest.model.Order;
import org.folio.rest.model.OrderBy;
import org.folio.rest.model.Status;

public interface NoteLinksRepository {

  Future<Void> updateNoteLinks(Link link, List<String> assignNotes, List<String> unAssignNotes, String tenantId);

  Future<NoteCollection> findNotesByQuery(Status status, Order order, OrderBy orderBy, String domain, String title,
                                          Link link, int limit, int offset, String tenantId);

  Future<Integer> countNotes(Status status, String domain, String title, Link link, String tenantId);
}
