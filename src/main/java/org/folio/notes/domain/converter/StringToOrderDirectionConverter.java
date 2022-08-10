package org.folio.notes.domain.converter;

import org.folio.notes.domain.dto.OrderDirection;
import org.springframework.core.convert.converter.Converter;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

@Component
public class StringToOrderDirectionConverter implements Converter<String, OrderDirection> {

  @Override
  public OrderDirection convert(@NonNull String source) {
    return OrderDirection.fromValue(source);
  }
}
