package org.folio.notes.mapper;

import java.util.List;

import org.mapstruct.InheritInverseConfiguration;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValueCheckStrategy;
import org.springframework.data.domain.Page;

import org.folio.notes.domain.dto.NoteType;
import org.folio.notes.domain.dto.NoteTypeCollection;
import org.folio.notes.domain.entity.TypeEntity;

@Mapper(componentModel = "spring", nullValueCheckStrategy = NullValueCheckStrategy.ALWAYS)
public interface TypeMapper {

  @Mapping(target = "metadata", expression = "java(toMetadata(entity))")
  NoteType toDto(TypeEntity entity);

  @InheritInverseConfiguration
  TypeEntity toEntity(NoteType dto);

  default NoteTypeCollection toDtoCollection(Page<TypeEntity> entityList) {
    return new NoteTypeCollection().noteTypes(toDtoList(entityList.getContent())).totalRecords(
      Math.toIntExact(entityList.getTotalElements()));
  }

  List<NoteType> toDtoList(List<TypeEntity> entityList);

  @Mapping(target = "id", ignore = true)
  @Mapping(target = "updatedDate", ignore = true)
  @Mapping(target = "updatedBy", ignore = true)
  @Mapping(target = "createdDate", ignore = true)
  @Mapping(target = "createdBy", ignore = true)
  TypeEntity updateNoteType(NoteType dto, @MappingTarget TypeEntity entity);
}
