package io.guestgraph.engine.persistence.mapper;

import io.guestgraph.engine.domain.Actor;
import io.guestgraph.engine.domain.Guest;
import io.guestgraph.engine.domain.IdentifierQualityRule;
import io.guestgraph.engine.domain.MatchReview;
import io.guestgraph.engine.domain.MergeEvent;
import io.guestgraph.engine.domain.NegativeMatchRule;
import io.guestgraph.engine.domain.NormalizedIdentifier;
import io.guestgraph.engine.domain.SourceRecord;
import io.guestgraph.engine.domain.SourceSystem;
import io.guestgraph.engine.domain.Tenant;
import io.guestgraph.engine.persistence.entity.GuestEntity;
import io.guestgraph.engine.persistence.entity.IdentifierEntity;
import io.guestgraph.engine.persistence.entity.IdentifierQualityRuleEntity;
import io.guestgraph.engine.persistence.entity.MatchReviewEntity;
import io.guestgraph.engine.persistence.entity.MergeEventEntity;
import io.guestgraph.engine.persistence.entity.NegativeMatchRuleEntity;
import io.guestgraph.engine.persistence.entity.RecordIdentifierEntity;
import io.guestgraph.engine.persistence.entity.SourceRecordEntity;
import io.guestgraph.engine.persistence.entity.SourceSystemEntity;
import io.guestgraph.engine.persistence.entity.TenantEntity;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

/**
 * Entity → domain-record mapping, generated at compile time. unmappedTargetPolicy ERROR fails the
 * build on a forgotten field — no silently dropped data. The write direction is deliberate
 * hand-written entity construction in the stores/adapter.
 */
@Mapper(
    componentModel = "spring",
    unmappedTargetPolicy = ReportingPolicy.ERROR,
    imports = Actor.class)
public interface DomainMappers {

  Tenant toDomain(TenantEntity entity);

  SourceSystem toDomain(SourceSystemEntity entity);

  Guest toDomain(GuestEntity entity);

  @Mapping(
      target = "actor",
      expression = "java(Actor.of(entity.getActorType(), entity.getActorId()))")
  MergeEvent toDomain(MergeEventEntity entity);

  @Mapping(target = "sourceSystemId", source = "sourceSystem.id")
  @Mapping(target = "sourceSystemCode", source = "sourceSystem.code")
  @Mapping(target = "payloadJson", source = "payload")
  SourceRecord toDomain(SourceRecordEntity entity);

  List<SourceRecord> toDomainRecords(List<SourceRecordEntity> entities);

  @Mapping(target = "value", source = "valueNormalized")
  NormalizedIdentifier toDomain(RecordIdentifierEntity entity);

  @Mapping(target = "value", source = "valueNormalized")
  NormalizedIdentifier toDomain(IdentifierEntity entity);

  List<NormalizedIdentifier> toDomainIdentifiers(List<IdentifierEntity> entities);

  MatchReview toDomain(MatchReviewEntity entity);

  List<MatchReview> toDomainReviews(List<MatchReviewEntity> entities);

  @Mapping(
      target = "actor",
      expression = "java(Actor.of(entity.getActorType(), entity.getActorId()))")
  @Mapping(
      target = "liftedActor",
      expression = "java(Actor.of(entity.getLiftedActorType(), entity.getLiftedActorId()))")
  NegativeMatchRule toDomain(NegativeMatchRuleEntity entity);

  List<NegativeMatchRule> toDomainNegativeRules(List<NegativeMatchRuleEntity> entities);

  @Mapping(target = "builtin", constant = "false")
  IdentifierQualityRule toDomain(IdentifierQualityRuleEntity entity);

  List<IdentifierQualityRule> toDomainQualityRules(List<IdentifierQualityRuleEntity> entities);
}
