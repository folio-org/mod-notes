package org.folio.spring.config;

import static org.folio.rest.exc.ExceptionPredicates.instanceOf;
import static org.folio.rest.exc.RestExceptionHandlers.badRequestHandler;
import static org.folio.rest.exc.RestExceptionHandlers.baseBadRequestHandler;
import static org.folio.rest.exc.RestExceptionHandlers.baseNotFoundHandler;
import static org.folio.rest.exc.RestExceptionHandlers.baseUnauthorizedHandler;
import static org.folio.rest.exc.RestExceptionHandlers.baseUnprocessableHandler;
import static org.folio.rest.exc.RestExceptionHandlers.completionCause;
import static org.folio.rest.exc.RestExceptionHandlers.generalHandler;
import static org.folio.rest.exc.RestExceptionHandlers.logged;
import static org.folio.rest.exceptions.NoteExceptionHandlers.entityValidationHandler;

import javax.ws.rs.NotAuthorizedException;
import javax.ws.rs.core.Response;

import org.folio.common.pf.PartialFunction;
import org.folio.config.ModConfiguration;
import org.folio.db.exc.translation.DBExceptionTranslator;
import org.folio.db.exc.translation.DBExceptionTranslatorFactory;
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
  "org.folio.links",
  "org.folio.userlookup"})
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
  public PartialFunction<Throwable, Response> notesExcHandler() {
    return logged(entityValidationHandler()
                .orElse(baseBadRequestHandler())
                .orElse(badRequestHandler(instanceOf(NotAuthorizedException.class)))
                .orElse(baseNotFoundHandler())
                .orElse(generalHandler())
                .compose(completionCause())); // extract the cause before applying any handler
  }

  @Bean
  public PartialFunction<Throwable, Response> noteTypesExcHandler() {
    return logged(baseBadRequestHandler()
      .orElse(baseNotFoundHandler())
      .orElse(baseUnauthorizedHandler())
      .orElse(baseUnprocessableHandler())
      .orElse(generalHandler())
      .compose(completionCause())); // extract the cause before applying any handler
  }

  @Bean
  public PartialFunction<Throwable, Response> noteLinksExcHandler() {
    return logged(baseBadRequestHandler()
      .orElse(badRequestHandler(instanceOf(IllegalArgumentException.class)))
      .orElse(baseNotFoundHandler())
      .orElse(generalHandler())
      .compose(completionCause())); // extract the cause before applying any handler
  }

  @Bean
  public DBExceptionTranslator excTranslator(@Value("${db.exception.translator.name}") String translatorName) {
    DBExceptionTranslatorFactory factory = DBExceptionTranslatorFactory.instance();
    return factory.create(translatorName);
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
