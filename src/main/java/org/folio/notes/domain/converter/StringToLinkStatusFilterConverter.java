package org.folio.notes.domain.converter;

import org.folio.notes.domain.dto.LinkStatusFilter;
import org.jspecify.annotations.NonNull;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

@Component
public class StringToLinkStatusFilterConverter implements Converter<String, LinkStatusFilter> {

  @Override
  public LinkStatusFilter convert(@NonNull String source) {
    return LinkStatusFilter.fromValue(source);
  }
}
