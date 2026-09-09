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
   * The tenant's events from an instant forward, inclusive, over {@code
   * merge_event_tenant_time_idx}: the first page of the split hop's scan. Inclusive because the
   * replay events an unmerge writes share its transaction and may share its timestamp, and a random
   * id can sort before the unmerge's own. Two queries rather than one with an OR on a nullable
   * cursor: under a generic plan the OR degrades to a filter over the tenant's whole history,
   * measured, while each plain predicate stays an index seek.
   */
  @Query(
      nativeQuery = true,
      value =
          """
            SELECT * FROM merge_event
            WHERE tenant_id = :tenantId AND created_at >= CAST(:from AS timestamptz)
            ORDER BY created_at, id
            LIMIT :limit
            """)
  List<MergeEventEntity> findFrom(
      @Param("tenantId") UUID tenantId, @Param("from") Instant from, @Param("limit") int limit);

  /** The pages after the first: strictly after the keyset {@code (created_at, id)}. */
  @Query(
      nativeQuery = true,
      value =
          """
            SELECT * FROM merge_event
            WHERE tenant_id = :tenantId
              AND (created_at, id) > (CAST(:after AS timestamptz), CAST(:afterId AS uuid))
            ORDER BY created_at, id
            LIMIT :limit
            """)
  List<MergeEventEntity> findAfter(
      @Param("tenantId") UUID tenantId,
      @Param("after") Instant after,
      @Param("afterId") UUID afterId,
      @Param("limit") int limit);
}
