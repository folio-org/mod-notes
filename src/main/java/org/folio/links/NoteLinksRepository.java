package org.folio.links;

import java.util.List;

import io.vertx.core.Future;

import org.folio.model.EntityLink;
import org.folio.model.Order;
import org.folio.model.OrderBy;
import org.folio.model.RowPortion;
import org.folio.model.Status;
import org.folio.rest.jaxrs.model.Link;
import org.folio.rest.jaxrs.model.NoteCollection;

public interface NoteLinksRepository {

  Future<Void> updateNoteLinks(Link link, List<String> assignNotes, List<String> unAssignNotes, String tenantId);

  Future<NoteCollection> findNotesByTitleAndStatus(EntityLink link, String title, Status status,
                                                   OrderBy orderBy, Order order,
                                                   RowPortion rowPortion, String tenantId);

  Future<Integer> countNotesWithTitleAndStatus(EntityLink link, String title, Status status, String tenantId);
}
