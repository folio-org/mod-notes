package org.folio.spring.config;

import static org.folio.rest.exc.ExceptionHandlers.badRequestHandler;
import static org.folio.rest.exc.ExceptionHandlers.generalHandler;
import static org.folio.rest.exc.ExceptionHandlers.logged;
import static org.folio.rest.exc.ExceptionHandlers.notFoundHandler;

import javax.ws.rs.core.Response;

import com.rits.cloning.Cloner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.core.io.ClassPathResource;

import org.folio.config.ModConfiguration;
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

  @Bean
  public PartialFunction<Throwable, Response> defaultExcHandler() {
    return logged(badRequestHandler()
                .orElse(notFoundHandler())
                .orElse(generalHandler()));
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
