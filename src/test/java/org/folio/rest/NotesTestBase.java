package org.folio.rest;

import static org.folio.test.util.TestUtil.STUB_TENANT;
import static org.folio.util.NoteTestData.NOTE_TYPE;
import static org.folio.util.NoteTestData.NOTE_TYPE2;
import static org.folio.util.NoteTestData.NOTE_TYPE2_ID;
import static org.folio.util.NoteTestData.NOTE_TYPE_ID;

import io.restassured.http.Header;
import io.restassured.response.ExtractableResponse;
import io.restassured.response.Response;
import io.vertx.ext.unit.TestContext;
import org.apache.http.HttpStatus;

import org.folio.okapi.common.XOkapiHeaders;
import org.folio.test.util.DBTestUtil;
import org.folio.test.util.TestBase;

public class NotesTestBase extends TestBase {

  public static final String NOTE_TYPE_TABLE = "note_type";
  public static final String NOTE_TABLE = "note_data";

  protected static final Header TENANT_HEADER = new Header(XOkapiHeaders.TENANT, STUB_TENANT);
  protected static final Header INCORRECT_HEADER = new Header(XOkapiHeaders.TENANT, "wrong");

  protected static void createNoteTypes(TestContext context) {
    vertx.executeBlocking(future -> {
        DBTestUtil.save(NOTE_TYPE_ID, NOTE_TYPE, vertx, NOTE_TYPE_TABLE);
        DBTestUtil.save(NOTE_TYPE2_ID, NOTE_TYPE2, vertx, NOTE_TYPE_TABLE);
        future.complete();
      },
      context.asyncAssertSuccess());
  }

  protected ExtractableResponse<Response> postNoteWithOk(String postBody, Header creator) {
    return postWithStatus("/notes", postBody, HttpStatus.SC_CREATED, creator);
  }

  protected ExtractableResponse<Response> postNoteTypeWithOk(String postBody, Header creator) {
    return postWithStatus("/note-types", postBody, HttpStatus.SC_CREATED, creator);
  }
}
