package org.folio.notes.domain.mapper;

import org.folio.notes.domain.dto.Link;
import org.folio.notes.domain.entity.LinkEntity;
import org.mapstruct.InheritInverseConfiguration;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.NullValueCheckStrategy;

@Mapper(componentModel = "spring", nullValueCheckStrategy = NullValueCheckStrategy.ALWAYS)
public interface LinkMapper {

  @Mapping(target = "id", source = "objectId")
  @Mapping(target = "type", source = "objectType")
  Link toDtoLink(LinkEntity entity);

  @Mapping(target = "id", ignore = true)
  @Mapping(target = "notes", ignore = true)
  @Mapping(target = "new", ignore = true)
  @InheritInverseConfiguration
  LinkEntity toEntityLink(Link dto);
}
