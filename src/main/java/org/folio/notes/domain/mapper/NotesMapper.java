package org.folio.notes.domain.mapper;

import org.folio.notes.domain.dto.Note;
import org.folio.notes.domain.entity.NoteEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValueCheckStrategy;

@Mapper(componentModel = "spring", nullValueCheckStrategy = NullValueCheckStrategy.ALWAYS,
  uses = {
    MetadataMapper.class,
    LinkMapper.class
  })
public interface NotesMapper {

  @Mapping(target = "metadata", source = "entity", qualifiedByName = "UserMetadataMapper")
  @Mapping(target = "typeId", expression = "java(entity.getType().getId())")
  @Mapping(target = "type", expression = "java(entity.getType().getName())")
  Note toDto(NoteEntity entity);

  @Mapping(target = "type.id", source = "typeId")
  @Mapping(target = "indexedContent", ignore = true)
  @Mapping(target = "createdDate", ignore = true)
  @Mapping(target = "updatedDate", ignore = true)
  @Mapping(target = "createdBy", ignore = true)
  @Mapping(target = "updatedBy", ignore = true)
  NoteEntity toEntity(Note dto);

  @Mapping(target = "id", ignore = true)
  @Mapping(target = "type.id", source = "typeId")
  @Mapping(target = "indexedContent", ignore = true)
  @Mapping(target = "updatedDate", ignore = true)
  @Mapping(target = "updatedBy", ignore = true)
  @Mapping(target = "createdDate", ignore = true)
  @Mapping(target = "createdBy", ignore = true)
  @Mapping(target = "type", ignore = true)
  NoteEntity updateNote(Note dto, @MappingTarget NoteEntity entity);
}
