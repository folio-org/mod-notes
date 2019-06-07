package org.folio.links;

import java.util.List;

import io.vertx.core.Future;

import org.folio.rest.jaxrs.model.Link;
import org.folio.rest.jaxrs.model.NoteCollection;
import org.folio.rest.model.Order;
import org.folio.rest.model.OrderBy;
import org.folio.rest.model.Status;

public interface NoteLinksRepository {

  Future<Void> putNoteLinkTypeIdToNote(Link link, String tenantId, List<String> assignNotes, List<String> unAssignNotes);

  Future<NoteCollection> getNoteCollection(Status status, String tenantId, Order order,
                                           OrderBy orderBy, String domain, String title, Link link, int limit, int offset);

  Future<Integer> getNoteCount(Status status, String domain, String title, Link link, String tenantId);
}
