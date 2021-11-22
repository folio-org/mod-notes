package org.folio.notes.domain.converter;

import org.springframework.core.convert.converter.Converter;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import org.folio.notes.domain.dto.LinkStatusFilter;

@Component
public class StringToLinkStatusFilterConverter implements Converter<String, LinkStatusFilter> {

  @Override
  public LinkStatusFilter convert(@NonNull String source) {
    return LinkStatusFilter.fromValue(source);
  }
}
