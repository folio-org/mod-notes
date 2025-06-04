package org.folio.notes.domain.mapper;

import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.UUID;
import org.folio.notes.domain.dto.Metadata;
import org.folio.notes.domain.dto.User;
import org.folio.notes.domain.dto.UserInfo;
import org.folio.notes.domain.entity.AuditableEntity;
import org.folio.notes.service.UsersService;
import org.mapstruct.AfterMapping;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Named;
import org.mapstruct.NullValueCheckStrategy;
import org.springframework.beans.factory.annotation.Autowired;

@Mapper(componentModel = "spring", nullValueCheckStrategy = NullValueCheckStrategy.ALWAYS, uses = UsersService.class)
public abstract class MetadataMapper {

  @Autowired
  private UsersService usersService;

  @Named("BaseMetadataMapper")
  @Mapping(target = "updatedByUserId", source = "updatedBy")
  @Mapping(target = "createdByUserId", source = "createdBy")
  @Mapping(target = "updatedByUsername", ignore = true)
  @Mapping(target = "createdByUsername", ignore = true)
  @Mapping(target = "createdBy", ignore = true)
  @Mapping(target = "updatedBy", ignore = true)
  abstract Metadata extractBaseMetadata(AuditableEntity entity);

  @Named("UserMetadataMapper")
  @BeanMapping(qualifiedByName = "ExtractUserInfo")
  @Mapping(target = "updatedByUserId", source = "updatedBy")
  @Mapping(target = "createdByUserId", source = "createdBy")
  @Mapping(target = "updatedByUsername", ignore = true)
  @Mapping(target = "createdByUsername", ignore = true)
  @Mapping(target = "createdBy", ignore = true)
  @Mapping(target = "updatedBy", ignore = true)
  abstract Metadata extractUserMetadata(AuditableEntity entity);

  @Named("ExtractUserInfo")
  @AfterMapping
  void extractUserInfo(AuditableEntity source, @MappingTarget Metadata target) {
    UUID createdByUserId = source.getCreatedBy();
    if (createdByUserId != null) {
      usersService.getUser(createdByUserId)
        .ifPresent(user -> {
          target.setCreatedByUsername(user.username());
          target.setCreatedBy(toUserDto(user.personal()));
        });
    }
    UUID updatedByUserId = source.getUpdatedBy();
    if (updatedByUserId != null) {
      usersService.getUser(updatedByUserId)
        .ifPresent(user -> {
          target.setUpdatedByUsername(user.username());
          target.setUpdatedBy(toUserDto(user.personal()));
        });
    }
  }

  abstract UserInfo toUserDto(User.UserPersonal source);

  OffsetDateTime map(Timestamp value) {
    return OffsetDateTime.from(value.toInstant().atZone(ZoneId.systemDefault()));
  }
}
