package org.folio.notes.config;

import org.folio.notes.client.UsersClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

@Configuration
public class HttpClientConfiguration {

  @Bean
  public UsersClient usersClient(HttpServiceProxyFactory factory) {
    return factory.createClient(UsersClient.class);
  }
}
