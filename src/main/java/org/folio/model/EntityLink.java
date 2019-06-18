package org.folio.model;

import lombok.Value;

@Value
public class EntityLink {

  private String domain;
  private String type;
  private String id;

}
