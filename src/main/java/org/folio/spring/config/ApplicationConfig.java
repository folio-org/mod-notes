package org.folio.spring.config;

import static org.folio.util.exc.ExceptionHandlers.badRequestHandler;
import static org.folio.util.exc.ExceptionHandlers.generalHandler;
import static org.folio.util.exc.ExceptionHandlers.logged;
import static org.folio.util.exc.ExceptionHandlers.notFoundHandler;

import javax.ws.rs.core.Response;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.core.io.ClassPathResource;

import org.folio.rest.tools.messages.Messages;
import org.folio.util.pf.PartialFunction;

@Configuration
@ComponentScan(basePackages = {"org.folio.type"})
public class ApplicationConfig {

  @Bean
  public PropertySourcesPlaceholderConfigurer placeholderConfigurer() {
    PropertySourcesPlaceholderConfigurer configurer = new PropertySourcesPlaceholderConfigurer();
    configurer.setLocation(new ClassPathResource("application.properties"));
    return configurer;
  }

  @Bean
  public Messages messages() {
    return Messages.getInstance();
  }

  @Bean("default")
  public PartialFunction<Throwable, Response> defaultExcHandler() {
    return logged(badRequestHandler()
                .orElse(notFoundHandler())
                .orElse(generalHandler()));
  }
}
