package org.folio.model;


import static org.apache.commons.lang3.StringUtils.defaultString;

import org.apache.commons.lang3.EnumUtils;

public enum Status {

  ASSIGNED,
  UNASSIGNED,
  ALL;

  public static boolean contains(String value) {
    return EnumUtils.isValidEnum(Status.class, defaultString(value).toUpperCase());
  }

  public static Status enumOf(String value) {
    return valueOf(defaultString(value).toUpperCase());
  }
}
