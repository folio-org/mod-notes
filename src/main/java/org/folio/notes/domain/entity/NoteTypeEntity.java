package org.folio.notes.domain.entity;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;
import javax.validation.constraints.NotBlank;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Entity
@Table(name = "type", uniqueConstraints = {
  @UniqueConstraint(name = "uc_type_name", columnNames = {"name"})
})
@Getter @Setter @ToString(onlyExplicitlyIncluded = true)
public class NoteTypeEntity extends AuditableEntity {

  @NotBlank
  @ToString.Include
  @Column(name = "name", nullable = false)
  private String name;

}
