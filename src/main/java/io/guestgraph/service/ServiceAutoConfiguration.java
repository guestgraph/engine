package io.guestgraph.service;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.ComponentScan;

/**
 * Brings the shared package into a service's context: a service scans from its own root, and this
 * package sits beside it, so it is registered as an auto-configuration through the vendored imports
 * file under META-INF/spring. Vendored from guestgraph/service-conventions, never edited in a
 * service.
 */
@AutoConfiguration
@ComponentScan(basePackageClasses = ServiceAutoConfiguration.class)
public class ServiceAutoConfiguration {}
