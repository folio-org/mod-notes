package org.folio.notes.mapper;

import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import org.apache.commons.lang3.StringUtils;
import org.mapstruct.InheritInverseConfiguration;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValueCheckStrategy;
import org.springframework.data.domain.Page;

import org.folio.notes.domain.dto.Metadata;
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

  @Mapping(target = "updatedByUserId", source = "updatedBy")
  @Mapping(target = "createdByUserId", source = "createdBy")
  Metadata toMetadata(TypeEntity entity);

  default OffsetDateTime map(Timestamp value) {
    return value != null ? OffsetDateTime.from(value.toInstant().atZone(ZoneId.systemDefault())) : null;
  }

  default String map(UUID value) {
    return value != null ? value.toString() : null;
  }

  default UUID map(String value) {
    return (StringUtils.isBlank(value)) ? null : UUID.fromString(value);
  }
}
