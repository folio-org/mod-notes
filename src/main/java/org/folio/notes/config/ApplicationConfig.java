package org.folio.notes.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ApplicationConfig {

  @Bean
  public OpenAPI openAPI() {
    return new OpenAPI()
      .info(new Info().title("Employee APIs").description("Description here.."));
  }
}
