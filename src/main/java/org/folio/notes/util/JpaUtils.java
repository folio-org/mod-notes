package org.folio.notes.util;

import java.util.UUID;
import lombok.experimental.UtilityClass;
import org.folio.notes.domain.entity.BaseEntity;
import org.jspecify.annotations.NonNull;

@UtilityClass
public class JpaUtils {

  public static <E extends BaseEntity> E initNewEntity(@NonNull E entity) {
    if (entity.getId() == null) {
      entity.setId(UUID.randomUUID());
    }
    entity.setNew(true);
    return entity;
  }
}
