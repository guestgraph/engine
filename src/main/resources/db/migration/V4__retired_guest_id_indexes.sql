-- Retired guest ids resolve by walking merge_event (slice 4). Nothing is written when a guest
-- is retired; these indexes keep the two lookups the walk makes cheap without charging every
-- ingest for them.

-- "Which event absorbed guest X": only merge and confirmed-review rows carry absorbed ids, so a
-- partial index is maintained only when a merge happens and stays proportional to merges.
CREATE INDEX merge_event_absorbed_gin
    ON merge_event USING gin (absorbed_guest_ids jsonb_path_ops)
    WHERE absorbed_guest_ids <> '[]'::jsonb;

-- The tenant's events from an instant forward: the split hop reads the replay events that
-- follow an UNMERGE in the same transaction. Append-friendly — inserts land at the right edge.
CREATE INDEX merge_event_tenant_time_idx ON merge_event (tenant_id, created_at, id);
