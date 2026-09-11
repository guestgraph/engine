package io.guestgraph.service;

import java.io.IOException;
import java.util.List;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

/**
 * Loads service-defaults.yaml, the settings every guestgraph service shares, beneath everything
 * else: a service's own configuration and its environment win over it. Registered through
 * META-INF/spring.factories, which the sync vendors with this class. Vendored from
 * guestgraph/service-conventions, never edited in a service.
 */
public class ServiceDefaults implements EnvironmentPostProcessor {

  static final String FILE = "service-defaults.yaml";

  @Override
  public void postProcessEnvironment(
      ConfigurableEnvironment environment, SpringApplication application) {
    try {
      List<PropertySource<?>> sources =
          new YamlPropertySourceLoader().load(FILE, new ClassPathResource(FILE));
      for (PropertySource<?> source : sources) {
        environment.getPropertySources().addLast(source);
      }
    } catch (IOException e) {
      throw new IllegalStateException("Cannot load " + FILE, e);
    }
  }
}
