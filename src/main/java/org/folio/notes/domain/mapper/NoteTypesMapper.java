package org.folio.notes.domain.mapper;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.folio.notes.domain.dto.NoteType;
import org.folio.notes.domain.dto.NoteTypeCollection;
import org.folio.notes.domain.dto.NoteTypeUsage;
import org.folio.notes.domain.entity.NoteTypeEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValueCheckStrategy;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.springframework.data.domain.Page;

@Mapper(componentModel = "spring", nullValueCheckStrategy = NullValueCheckStrategy.ALWAYS, uses = MetadataMapper.class)
public interface NoteTypesMapper {

  @Mapping(target = "usage", ignore = true)
  @Mapping(target = "metadata", source = "entity", qualifiedByName = "BaseMetadataMapper")
  NoteType toDto(NoteTypeEntity entity);

  @Mapping(target = "createdDate", ignore = true)
  @Mapping(target = "updatedDate", ignore = true)
  @Mapping(target = "createdBy", ignore = true)
  @Mapping(target = "updatedBy", ignore = true)
  NoteTypeEntity toEntity(NoteType dto);

  default NoteTypeCollection toDtoCollection(Page<NoteTypeEntity> entityList, Map<UUID, Boolean> noteTypeUsage) {
    var noteTypes = toDtoList(entityList.getContent());
    noteTypes.forEach(noteType -> noteType.setUsage(getNoteTypeUsage(noteType.getId(), noteTypeUsage)));
    return new NoteTypeCollection()
      .noteTypes(noteTypes)
      .totalRecords(Math.toIntExact(entityList.getTotalElements()));
  }

  List<NoteType> toDtoList(List<NoteTypeEntity> entityList);

  @Mapping(target = "id", ignore = true)
  @Mapping(target = "createdDate", ignore = true)
  @Mapping(target = "updatedDate", ignore = true)
  @Mapping(target = "createdBy", ignore = true)
  @Mapping(target = "updatedBy", ignore = true)
  @Mapping(target = "name", source = "name", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
  NoteTypeEntity updateNoteType(NoteType dto, @MappingTarget NoteTypeEntity entity);

  default NoteTypeUsage getNoteTypeUsage(UUID id, Map<UUID, Boolean> noteTypeUsage) {
    var isAssigned = noteTypeUsage.getOrDefault(id, false);
    return new NoteTypeUsage().isAssigned(isAssigned);
  }
}
