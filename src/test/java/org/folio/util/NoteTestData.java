package org.folio.util;

import static org.folio.test.util.TestUtil.readFile;

import java.io.IOException;
import java.net.URISyntaxException;

import io.restassured.http.Header;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import org.folio.okapi.common.XOkapiHeaders;

public class NoteTestData {
  private static final Logger logger = LoggerFactory.getLogger(NoteTestData.class);

  public static final String PACKAGE_ID = "18-2356521";
  public static final String PACKAGE_ID2 = "123-456789";
  public static final String PACKAGE_TYPE = "package";
  public static final String DOMAIN = "eholdings";

  public static final String NOTE_TYPE_ID = "2af21797-d25b-46dc-8427-1759d1db2057";
  public static final String NOTE_TYPE2_ID = "13f21797-d25b-46dc-8427-1759d1db2057";
  public static final String NOTE_TYPE_NAME = "High Priority";
  public static final String NOTE_TYPE2_NAME = "test note";

  public static final Header USER19 = new Header(XOkapiHeaders.USER_ID, "11999999-9999-4999-9999-999999999911");
  public static final Header USER8 = new Header(XOkapiHeaders.USER_ID, "88888888-8888-4888-8888-888888888888");
  public static final Header USER9 = new Header(XOkapiHeaders.USER_ID, "99999999-9999-4999-9999-999999999999");

  public static final String NOTE_1;
  public static final String NOTE_2;
  public static final String UPDATE_NOTE_REQUEST;
  public static final String UPDATE_NOTE_REQUEST_WITH_LINKS;
  public static final String NOTE_3;
  public static final String NOTE_4;
  public static final String UPDATE_NOTE_4_REQUEST;
  public static final String UPDATE_NOTE_5_REQUEST_WITH_NON_EXISTING_TYPE_ID;
  public static final String NOTE_5_LONG_TITLE;
  public static final String NOTE_6_EMPTY_CONTENT;
  public static final String NOTE_TYPE;
  public static final String NOTE_TYPE2;
  public static final String UPDATE_NOTE_2_REQUEST_WITH_NO_LINKS;

  static {
    try {
      NOTE_1 = readFile("note/note1.json");
      NOTE_2 = readFile("note/note2.json");
      UPDATE_NOTE_REQUEST = readFile("note/updateNoteRequest.json");
      UPDATE_NOTE_REQUEST_WITH_LINKS = readFile("note/updateNoteRequestWithLinks.json");
      NOTE_3 = readFile("note/note3.json");
      NOTE_4 = readFile("note/note4.json");
      UPDATE_NOTE_4_REQUEST = readFile("note/updateNote4Request.json");
      UPDATE_NOTE_5_REQUEST_WITH_NON_EXISTING_TYPE_ID = readFile("note/updateNote5RequestWithNonExistingTypeId.json");
      NOTE_5_LONG_TITLE = readFile("note/note5LongTitle.json");
      NOTE_6_EMPTY_CONTENT = readFile("note/note6EmptyContent.json");
      NOTE_TYPE = readFile("notetype/noteType.json");
      NOTE_TYPE2 = readFile("notetype/noteType2.json");
      UPDATE_NOTE_2_REQUEST_WITH_NO_LINKS = readFile("note/updateNoteRequestWithNoId.json");
    } catch (IOException | URISyntaxException e) {
      logger.error("Can't read test files", e);
      throw new IllegalStateException(e);
    }
  }
}
