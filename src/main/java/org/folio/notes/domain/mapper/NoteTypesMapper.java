package org.folio.notes.domain.mapper;

import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValueCheckStrategy;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.springframework.data.domain.Page;

import org.folio.notes.domain.dto.NoteType;
import org.folio.notes.domain.dto.NoteTypeCollection;
import org.folio.notes.domain.entity.NoteTypeEntity;

@Mapper(componentModel = "spring", nullValueCheckStrategy = NullValueCheckStrategy.ALWAYS, uses = MetadataMapper.class)
public interface NoteTypesMapper {

  @Mapping(target = "metadata", source = ".", qualifiedByName = "BaseMetadataMapper")
  NoteType toDto(NoteTypeEntity entity);

  @Mapping(target = "createdDate", ignore = true)
  @Mapping(target = "updatedDate", ignore = true)
  @Mapping(target = "createdBy", ignore = true)
  @Mapping(target = "updatedBy", ignore = true)
  NoteTypeEntity toEntity(NoteType dto);

  default NoteTypeCollection toDtoCollection(Page<NoteTypeEntity> entityList) {
    return new NoteTypeCollection()
      .noteTypes(toDtoList(entityList.getContent()))
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

}
