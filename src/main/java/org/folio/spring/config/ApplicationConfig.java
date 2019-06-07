package org.folio.spring.config;

import static org.folio.rest.exc.RestExceptionHandlers.badRequestHandler;
import static org.folio.rest.exc.RestExceptionHandlers.completionCause;
import static org.folio.rest.exc.RestExceptionHandlers.generalHandler;
import static org.folio.rest.exc.RestExceptionHandlers.logged;
import static org.folio.rest.exc.RestExceptionHandlers.notFoundHandler;
import static org.folio.rest.exceptions.NoteExceptionHandlers.badRequestExtendedHandler;
import static org.folio.rest.exceptions.NoteExceptionHandlers.entityValidationHandler;

import javax.ws.rs.core.Response;

import org.folio.common.pf.PartialFunction;
import org.folio.config.ModConfiguration;
import org.folio.rest.tools.messages.Messages;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.core.io.ClassPathResource;

import com.rits.cloning.Cloner;

@Configuration
@ComponentScan(basePackages = {
  "org.folio.type",
  "org.folio.note",
  "org.folio.links"})
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

  @Bean
  public PartialFunction<Throwable, Response> defaultExcHandler() {
    return logged(entityValidationHandler()
                .orElse(badRequestHandler())
                .orElse(badRequestExtendedHandler())
                .orElse(notFoundHandler())
                .orElse(generalHandler())
                .compose(completionCause())); // extract the cause before applying any handler
  }

  @Bean
  public org.folio.config.Configuration configuration(@Value("${note.configuration.module}") String module) {
    return new ModConfiguration(module);
  }

  @Bean
  public Cloner restModelCloner() {
    return new Cloner();
  }
}
