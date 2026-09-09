package io.guestgraph.persistence.repo;

import io.guestgraph.persistence.entity.MergeEventEntity;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface MergeEventRepo extends Repository<MergeEventEntity, UUID> {

  /** All events whose surviving guest is one of {@code guestIds}, oldest first. */
  @Query(
      """
            select e from MergeEventEntity e
            where e.tenantId = :tenantId and e.guestId in :guestIds
            order by e.createdAt, e.id
            """)
  List<MergeEventEntity> findByGuestIds(
      @Param("tenantId") UUID tenantId, @Param("guestIds") Collection<UUID> guestIds);

  /**
   * Native: jsonb containment has no JPQL form. {@code needle} is a one-element JSON array. The
   * non-empty predicate is repeated so the planner can use the partial index {@code
   * merge_event_absorbed_gin}, which covers only rows that absorbed something.
   */
  @Query(
      nativeQuery = true,
      value =
          """
            SELECT * FROM merge_event
            WHERE tenant_id = :tenantId
              AND absorbed_guest_ids <> '[]'::jsonb
              AND absorbed_guest_ids @> CAST(:needle AS jsonb)
            ORDER BY created_at, id
            """)
  List<MergeEventEntity> findAbsorbing(
      @Param("tenantId") UUID tenantId, @Param("needle") String needle);

  /**
   * The tenant's events from an instant forward, keyset-paged on {@code (created_at, id)} over
   * {@code merge_event_tenant_time_idx}. A null {@code afterId} starts at {@code from} inclusive
   * (the replay events an unmerge writes may share its timestamp); otherwise strictly after the
   * keyset, the same shape {@code MatchReviewRepo.list} uses.
   */
  @Query(
      nativeQuery = true,
      value =
          """
            SELECT * FROM merge_event
            WHERE tenant_id = :tenantId
              AND ((CAST(:afterId AS uuid) IS NULL AND created_at >= CAST(:from AS timestamptz))
                   OR (created_at, id) > (CAST(:from AS timestamptz), CAST(:afterId AS uuid)))
            ORDER BY created_at, id
            LIMIT :limit
            """)
  List<MergeEventEntity> findSince(
      @Param("tenantId") UUID tenantId,
      @Param("from") Instant from,
      @Param("afterId") UUID afterId,
      @Param("limit") int limit);
}
