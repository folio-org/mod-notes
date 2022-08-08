package org.folio.notes.domain.entity.projection;

import java.util.UUID;

public interface NoteTypeCount {

  UUID getTypeId();
  Boolean getIsAssigned();
}
