package org.folio.links;

import static org.folio.links.NoteLinksConstants.ANY_STRING_PATTERN;
import static org.folio.links.NoteLinksConstants.COUNT_NOTES_BY_DOMAIN_AND_TITLE;
import static org.folio.links.NoteLinksConstants.HAS_LINK_CONDITION;
import static org.folio.links.NoteLinksConstants.LIMIT_OFFSET;
import static org.folio.links.NoteLinksConstants.NOTE_TABLE;
import static org.folio.links.NoteLinksConstants.ORDER_BY_STATUS_CLAUSE;
import static org.folio.links.NoteLinksConstants.ORDER_BY_TITLE_CLAUSE;
import static org.folio.links.NoteLinksConstants.SELECT_NOTES_BY_DOMAIN_AND_TITLE;
import static org.folio.links.NoteLinksConstants.WORD_PATTERN;

import java.util.Collections;
import java.util.List;

import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import org.apache.commons.lang3.StringUtils;

import org.folio.rest.jaxrs.model.Link;
import org.folio.rest.model.Order;
import org.folio.rest.model.OrderBy;
import org.folio.rest.model.Status;
import org.folio.rest.persist.PostgresClient;

public class NoteLinksDbQueryHelper {

  public static JsonArray createAssignParameters(List<String> notesIds, Link link) {
    String jsonLink = Json.encode(link);
    JsonArray parameters = new JsonArray();
    parameters
      .add(jsonLink)
      .add(jsonLink);
    notesIds.forEach(parameters::add);
    return parameters;
  }

  public static JsonArray createUnAssignParameters(List<String> notesIds, Link link) {
    String jsonLink = Json.encode(link);
    JsonArray parameters = new JsonArray();
    parameters
      .add(jsonLink);
    notesIds.forEach(parameters::add);
    parameters.add(jsonLink);
    return parameters;
  }

  public static String getTableName(String tenantId) {
    return PostgresClient.convertToPsqlStandard(tenantId) + "." + NOTE_TABLE;
  }

  public static String createIdPlaceholders(int amountOfIds) {
    return StringUtils.join(Collections.nCopies(amountOfIds, "?"), ", ");
  }

  public static void addLimitOffset(JsonArray parameters, StringBuilder query, int limit, int offset) {
    query.append(LIMIT_OFFSET);
    parameters
      .add(limit)
      .add(offset);
  }

  public static void addSelectClause(JsonArray parameters, StringBuilder query, String tenantId, String domain, String title) {
    query.append(String.format(SELECT_NOTES_BY_DOMAIN_AND_TITLE, getTableName(tenantId)));
    parameters
      .add(domain)
      .add(getTitleRegexp(title));
  }

  public static void addSelectCountClause(JsonArray parameters, StringBuilder query, String tenantId, String domain, String title) {
    query.append(String.format(COUNT_NOTES_BY_DOMAIN_AND_TITLE, getTableName(tenantId)));
    parameters
      .add(domain)
      .add(getTitleRegexp(title));
  }

  public static void addOrderByClause(JsonArray parameters, StringBuilder query, Order order, OrderBy orderBy, String jsonLink) {
    if (orderBy == OrderBy.STATUS) {
      query.append(String.format(ORDER_BY_STATUS_CLAUSE, order.toString()));
      parameters.add(jsonLink);
    } else {
      query.append(String.format(ORDER_BY_TITLE_CLAUSE, order.toString()));
    }
  }

  public static void addWhereClause(JsonArray parameters, StringBuilder query, Status status, String jsonLink) {
    switch (status) {
      case ASSIGNED:
        query.append("AND " + HAS_LINK_CONDITION);
        parameters.add(jsonLink);
        break;
      case UNASSIGNED:
        query.append("AND NOT " + HAS_LINK_CONDITION);
        parameters.add(jsonLink);
        break;
    }
  }

  private static String getTitleRegexp(String title) {
    if (StringUtils.isEmpty(title)) {
      return ANY_STRING_PATTERN;
    } else {
      return String.format(WORD_PATTERN, title);
    }
  }
}
