package org.folio.links;

import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.lang3.mutable.MutableObject;
import org.folio.model.EntityLink;
import org.folio.model.Order;
import org.folio.model.OrderBy;
import org.folio.model.RowPortion;
import org.folio.model.Status;
import org.folio.rest.jaxrs.model.Link;
import org.folio.rest.jaxrs.model.NoteCollection;
import org.folio.rest.jaxrs.model.NoteLinkPut;
import org.folio.rest.jaxrs.model.NoteLinksPut;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import io.vertx.core.Future;

@Component
public class NoteLinksServiceImpl implements NoteLinksService {

  @Autowired
  private NoteLinksRepository noteLinksRepository;

  @Override
  public Future<Void> updateNoteLinks(NoteLinksPut entity, Link link, String tenantId) {
    List<String> assignNotes = getNoteIdsByStatus(entity, NoteLinkPut.Status.ASSIGNED);
    List<String> unAssignNotes = getNoteIdsByStatus(entity, NoteLinkPut.Status.UNASSIGNED);

    return noteLinksRepository.updateNoteLinks(link, assignNotes, unAssignNotes, tenantId);
  }

  @Override
  public Future<NoteCollection> findNotesByTitleAndStatus(EntityLink link, String title, Status status,
                                                          OrderBy orderBy, Order order, RowPortion rowPortion, String tenantId) {
    MutableObject<Integer> mutableTotalRecords = new MutableObject<>();

    String trimmedTitle = title != null ? title.trim() : "";
    return noteLinksRepository.countNotesWithTitleAndStatus(link, trimmedTitle, status, tenantId)
      .compose(count -> {
        mutableTotalRecords.setValue(count);
        return noteLinksRepository.findNotesByTitleAndStatus(link, trimmedTitle, status, orderBy, order, rowPortion,
          tenantId);
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
