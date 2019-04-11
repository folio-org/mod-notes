package org.folio.util;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;

import io.vertx.core.json.Json;
import org.apache.commons.io.FileUtils;

public class TestUtil {

  /**
   * Reads file from classpath as String
   */
  public static String readFile(String filename) throws IOException, URISyntaxException {
    return FileUtils.readFileToString(getFile(filename), StandardCharsets.UTF_8);
  }

  /**
   * Returns File object corresponding to the file on classpath with specified filename
   */
  public static File getFile(String filename) throws URISyntaxException {
    return new File(TestUtil.class.getClassLoader()
      .getResource(filename).toURI());
  }

  public static String toJson(Object object) {
    return Json.encode(object);
  }
}
