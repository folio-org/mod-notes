package org.folio.links;

import java.util.List;
import java.util.stream.Collectors;

import io.vertx.core.Future;
import org.apache.commons.lang3.mutable.MutableObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.folio.rest.jaxrs.model.Link;
import org.folio.rest.jaxrs.model.NoteCollection;
import org.folio.rest.jaxrs.model.NoteLinkPut;
import org.folio.rest.jaxrs.model.NoteLinksPut;
import org.folio.rest.model.Order;
import org.folio.rest.model.OrderBy;
import org.folio.rest.model.Status;

@Component
public class NoteLinksServiceImpl implements NoteLinksService {

  @Autowired
  private NoteLinksRepository noteLinksRepository;

  @Override
  public Future<Void> updateNoteLinks(NoteLinksPut entity, Link link, String tenantId) {
    List<String> assignNotes = getNoteIdsByStatus(entity, NoteLinkPut.Status.ASSIGNED);
    List<String> unAssignNotes = getNoteIdsByStatus(entity, NoteLinkPut.Status.UNASSIGNED);

    return noteLinksRepository.updateNoteLinks(link, tenantId, assignNotes,
      unAssignNotes);
  }

  @Override
  public Future<NoteCollection> getNoteCollection(Status parsedStatus, String tenantId, Order parsedOrder,
                                                  OrderBy parsedOrderBy, String domain, String title, Link link, int limit, int offset) {
    MutableObject<Integer> mutableTotalRecords = new MutableObject<>();
    return noteLinksRepository.getNoteCount(parsedStatus, domain, title, link, tenantId)
      .compose(count -> {
        mutableTotalRecords.setValue(count);
        return noteLinksRepository.getNoteCollection(parsedStatus, tenantId, parsedOrder, parsedOrderBy,
          domain, title,
          link, limit, offset);
      })
      .map(notes -> {
        notes.setTotalRecords(mutableTotalRecords.getValue());
        return notes;
      });
  }

  private List<String> getNoteIdsByStatus(NoteLinksPut entity, NoteLinkPut.Status status) {
    return entity.getNotes().stream()
      .filter(note -> status.equals(note.getStatus()))
      .map(NoteLinkPut::getId)
      .collect(Collectors.toList());
  }
}
