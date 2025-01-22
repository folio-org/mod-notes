package org.folio.notes.domain.mapper;

import java.util.List;
import org.folio.notes.domain.dto.Note;
import org.folio.notes.domain.dto.NoteCollection;
import org.folio.notes.domain.entity.NoteEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.NullValueCheckStrategy;
import org.springframework.data.domain.Page;

@Mapper(componentModel = "spring", nullValueCheckStrategy = NullValueCheckStrategy.ALWAYS,
  uses = {
    MetadataMapper.class,
    LinkMapper.class
  }
)
public interface NoteCollectionMapper {

  @Mapping(target = "metadata", source = "entity", qualifiedByName = "BaseMetadataMapper")
  @Mapping(target = "typeId", expression = "java(entity.getType().getId())")
  @Mapping(target = "type", expression = "java(entity.getType().getName())")
  Note toDto(NoteEntity entity);

  default NoteCollection toDtoCollection(Page<NoteEntity> entityList) {
    return new NoteCollection()
      .notes(toDtoList(entityList.getContent()))
      .totalRecords(Math.toIntExact(entityList.getTotalElements()));
  }

  List<Note> toDtoList(List<NoteEntity> entityList);

}
