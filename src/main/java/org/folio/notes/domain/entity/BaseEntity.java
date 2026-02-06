package org.folio.notes.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Transient;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.domain.Persistable;

@Getter
@Setter
@MappedSuperclass
public abstract class BaseEntity implements Persistable<UUID> {

  @Id
  @Column(name = "id", updatable = false, nullable = false)
  private UUID id;

  private @Transient boolean isNew = false;

  @PostLoad
  @PostPersist
  void markNotNew() {
    this.isNew = false;
  }
}
