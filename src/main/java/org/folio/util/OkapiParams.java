package org.folio.util;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Map;

import io.vertx.core.http.CaseInsensitiveHeaders;
import org.apache.commons.lang3.builder.ToStringBuilder;

import org.folio.okapi.common.XOkapiHeaders;

public class OkapiParams {

  private CaseInsensitiveHeaders headers;

  private String host;
  private int port;
  private String token;
  private String tenant;


  public OkapiParams(Map<String, String> headers) {
    this.headers = new CaseInsensitiveHeaders();
    this.headers.addAll(headers);

    this.token = headers.get(XOkapiHeaders.TOKEN);
    this.tenant = headers.get(XOkapiHeaders.TENANT);

    URL url;
    try {
      url = new URL(headers.get(XOkapiHeaders.URL));
    } catch (MalformedURLException e) {
      throw new IllegalArgumentException("Okapi url header contains invalid value: " + headers.get(XOkapiHeaders.URL));
    }
    this.host = url.getHost();
    this.port = url.getPort() != -1 ? url.getPort() : url.getDefaultPort();
  }

  public CaseInsensitiveHeaders getAllHeaders() {
    // make a copy to avoid any modifications to contained headers
    CaseInsensitiveHeaders result = new CaseInsensitiveHeaders();
    result.addAll(headers);
    return result;
  }

  public String getToken() {
    return token;
  }

  public String getTenant() {
    return tenant;
  }

  public String getHost() {
    return host;
  }

  public int getPort() {
    return port;
  }

  @Override
  public String toString() {
    return new ToStringBuilder(this)
      .append("host", host)
      .append("port", port)
      .append("tenant", tenant)
      .append("token", token)
      .append("headers", headers)
      .build();
  }
}
