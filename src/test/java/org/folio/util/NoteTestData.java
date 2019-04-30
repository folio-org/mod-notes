package org.folio.util;

import java.io.IOException;
import java.net.URISyntaxException;

import io.restassured.http.Header;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import org.folio.okapi.common.XOkapiHeaders;

public class NoteTestData {
  private static final Logger logger = LoggerFactory.getLogger(NoteTestData.class);

  public static final String PACKAGE_ID = "18-2356521";
  public static final String PACKAGE_TYPE = "package";
  public static final String DOMAIN = "eholdings";

  public static final String NOTE_TYPE_ID = "2af21797-d25b-46dc-8427-1759d1db2057";
  public static final String NOTE_TYPE2_ID = "13f21797-d25b-46dc-8427-1759d1db2057";
  public static final String NOTE_TYPE_NAME = "High Priority";
  public static final String NOTE_TYPE2_NAME = "test note";

  public static final Header USER19 = new Header(XOkapiHeaders.USER_ID, "11999999-9999-4999-9999-999999999911");
  public static final Header USER8 = new Header(XOkapiHeaders.USER_ID, "88888888-8888-4888-8888-888888888888");

  public static final String NOTE_1;
  public static final String NOTE_2;
  public static final String UPDATE_NOTE_REQUEST;
  public static final String UPDATE_NOTE_REQUEST_WITH_LINKS;
  public static final String NOTE_3;
  public static final String NOTE_4;
  public static final String UPDATE_NOTE_4_REQUEST;
  public static final String UPDATE_NOTE_5_REQUEST_WITH_NON_EXISTING_TYPE_ID;
  public static final String NOTE_TYPE;
  public static final String NOTE_TYPE2;
  public static final String UPDATE_NOTE_2_REQUEST_WITH_NO_LINKS;

  static {
    try {
      NOTE_1 = TestUtil.readFile("note/note1.json");
      NOTE_2 = TestUtil.readFile("note/note2.json");
      UPDATE_NOTE_REQUEST = TestUtil.readFile("note/updateNoteRequest.json");
      UPDATE_NOTE_REQUEST_WITH_LINKS = TestUtil.readFile("note/updateNoteRequestWithLinks.json");
      NOTE_3 = TestUtil.readFile("note/note3.json");
      NOTE_4 = TestUtil.readFile("note/note4.json");
      UPDATE_NOTE_4_REQUEST = TestUtil.readFile("note/updateNote4Request.json");
      UPDATE_NOTE_5_REQUEST_WITH_NON_EXISTING_TYPE_ID = TestUtil.readFile("note/updateNote5RequestWithNonExistingTypeId.json");
      NOTE_TYPE = TestUtil.readFile("notetype/noteType.json");
      NOTE_TYPE2 = TestUtil.readFile("notetype/noteType2.json");
      UPDATE_NOTE_2_REQUEST_WITH_NO_LINKS = TestUtil.readFile("note/updateNoteRequestWithNoId.json");
    } catch (IOException | URISyntaxException e) {
      logger.error("Can't read test files", e);
      throw new IllegalStateException(e);
    }
  }
}
