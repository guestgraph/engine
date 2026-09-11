package io.guestgraph.engine.config;

import io.guestgraph.engine.resolution.CompositeStrategy;
import io.guestgraph.engine.resolution.DeterministicMatcher;
import io.guestgraph.engine.resolution.ExplainOperation;
import io.guestgraph.engine.resolution.FuzzyMatcher;
import io.guestgraph.engine.resolution.GraphPort;
import io.guestgraph.engine.resolution.GuestIdResolver;
import io.guestgraph.engine.resolution.MatchingPolicy;
import io.guestgraph.engine.resolution.ResolutionEngine;
import io.guestgraph.engine.resolution.ResolutionStrategy;
import io.guestgraph.engine.resolution.ReviewDecisionOperation;
import io.guestgraph.engine.resolution.UnmergeOperation;
import io.guestgraph.engine.survivorship.GoldenProfileDeriver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ResolutionConfig {

  @Bean
  public GoldenProfileDeriver goldenProfileDeriver() {
    return new GoldenProfileDeriver();
  }

  @Bean
  public ResolutionStrategy resolutionStrategy(GraphPort graph) {
    return new CompositeStrategy(
        new DeterministicMatcher(),
        new FuzzyMatcher(),
        new MatchingPolicy(),
        graph::matchingConfig);
  }

  @Bean
  public ResolutionEngine resolutionEngine(
      GraphPort graph, ResolutionStrategy strategy, GoldenProfileDeriver profileDeriver) {
    return new ResolutionEngine(graph, strategy, profileDeriver);
  }

  @Bean
  public ExplainOperation explainOperation(GraphPort graph) {
    return new ExplainOperation(graph);
  }

  @Bean
  public GuestIdResolver guestIdResolver(GraphPort graph) {
    return new GuestIdResolver(graph);
  }

  @Bean
  public UnmergeOperation unmergeOperation(GraphPort graph, ResolutionEngine engine) {
    return new UnmergeOperation(graph, engine);
  }

  @Bean
  public ReviewDecisionOperation reviewDecisionOperation(GraphPort graph, ResolutionEngine engine) {
    return new ReviewDecisionOperation(graph, engine);
  }
}
