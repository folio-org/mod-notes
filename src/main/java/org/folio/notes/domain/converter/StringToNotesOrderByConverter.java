package org.folio.notes.domain.converter;

import org.folio.notes.domain.dto.NotesOrderBy;
import org.springframework.core.convert.converter.Converter;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

@Component
public class StringToNotesOrderByConverter implements Converter<String, NotesOrderBy> {

  @Override
  public NotesOrderBy convert(@NonNull String source) {
    return NotesOrderBy.fromValue(source);
  }
}
