package io.guestgraph.engine.persistence;

import io.guestgraph.engine.domain.Credential;
import io.guestgraph.engine.persistence.repo.TenantRepo;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Domain-facing tenant lookups (auth filter runs outside any transaction). */
@Component
public class TenantStore {

  private final TenantRepo tenantRepo;

  public TenantStore(TenantRepo tenantRepo) {
    this.tenantRepo = tenantRepo;
  }

  public Optional<Credential> findCredentialByApiKeyHash(String keyHash) {
    return tenantRepo.findCredentialByApiKeyHash(keyHash);
  }
}
