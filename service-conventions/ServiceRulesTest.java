import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaAnnotation;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.io.IOException;
import java.lang.reflect.Parameter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.data.repository.Repository;

/**
 * The persistence guardrails every guestgraph service on the Spring stack holds, vendored from
 * guestgraph/service-conventions and never edited in a service: every repository query is explicit
 * and scoped by the service's own key, tenant or connection, repository scaffolding that bypasses
 * it is banned, no ad-hoc EntityManager queries, raw SQL only where the service names it, and JPA
 * confined to the persistence package. In the default package on purpose: one file runs in every
 * service, reading the root, the scope and the exemptions from service-conventions.json.
 */
class ServiceRulesTest {

  static JavaClasses appClasses;
  static String root;
  static String scope;
  static List<String> jdbcClientAllowed;

  static final DescribedPredicate<JavaClass> SPRING_DATA_REPOSITORY =
      new DescribedPredicate<>("are Spring Data repositories") {
        @Override
        public boolean test(JavaClass javaClass) {
          return javaClass.isAssignableTo(Repository.class);
        }
      };

  /** A method annotated with the service's own agnostic marker, found by its name's ending. */
  static final DescribedPredicate<JavaAnnotation<?>> SCOPE_AGNOSTIC =
      new DescribedPredicate<>("are the service's *Agnostic annotation") {
        @Override
        public boolean test(JavaAnnotation<?> annotation) {
          return annotation.getRawType().getSimpleName().endsWith("Agnostic");
        }
      };

  @BeforeAll
  static void importClasses() throws IOException {
    String pin = Files.readString(Path.of("service-conventions.json"));
    root = field(pin, "root");
    scope = field(pin, "scope");
    jdbcClientAllowed = list(pin, "jdbcClientAllowed");
    appClasses =
        new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests())
            // Spring AOT artifacts are generated from the reviewed source; the rules target
            // what people write.
            .withImportOption(location -> !location.contains("__"))
            .importPackages(root);
  }

  @Test
  void noRepositoryScaffolding() {
    noClasses()
        .should()
        .beAssignableTo(CrudRepository.class)
        .orShould()
        .beAssignableTo(PagingAndSortingRepository.class)
        .because("derived and generic repository methods bypass the reviewed @Query surface")
        .check(appClasses);
  }

  @Test
  void everyRepositoryMethodIsScoped() {
    // Scoped by type, not package: a Repository declared anywhere is held to the rule.
    methods()
        .that()
        .areDeclaredInClassesThat(SPRING_DATA_REPOSITORY)
        .should(
            new ArchCondition<>(
                "take a " + scope + " parameter or carry an *Agnostic annotation with a reason") {
              @Override
              public void check(JavaMethod method, ConditionEvents events) {
                if (method.isAnnotatedWith(SCOPE_AGNOSTIC)) {
                  return;
                }
                boolean scoped =
                    Arrays.stream(method.reflect().getParameters())
                        .map(Parameter::getName)
                        .anyMatch(scope::equals);
                if (!scoped) {
                  events.add(
                      SimpleConditionEvent.violated(
                          method,
                          method.getFullName()
                              + " has no "
                              + scope
                              + " parameter and no *Agnostic annotation"));
                }
              }
            })
        .because("a repository query without the scope predicate reads across " + scope + "s")
        .check(appClasses);
  }

  @Test
  void everyRepositoryMethodDeclaresAnExplicitQuery() {
    methods()
        .that()
        .areDeclaredInClassesThat(SPRING_DATA_REPOSITORY)
        .should()
        .beAnnotatedWith(Query.class)
        .because(
            "every repository method is explicit JPQL or SQL, reviewable in one place; a derived"
                + " method could carry a decorative scope parameter that is never bound")
        .check(appClasses);
  }

  @Test
  void noAdHocEntityManagerQueries() {
    classes()
        .should(
            new ArchCondition<>("not create ad-hoc EntityManager queries") {
              @Override
              public void check(JavaClass javaClass, ConditionEvents events) {
                javaClass.getMethodCallsFromSelf().stream()
                    .filter(
                        call ->
                            "jakarta.persistence.EntityManager"
                                    .equals(call.getTargetOwner().getName())
                                && call.getName().startsWith("create"))
                    .forEach(
                        call ->
                            events.add(
                                SimpleConditionEvent.violated(
                                    javaClass,
                                    call.getDescription()
                                        + " — EntityManager.create*Query bypasses the reviewed"
                                        + " @Query surface")));
              }
            })
        .because("all query text lives in repository @Query annotations")
        .check(appClasses);
  }

  @Test
  void jdbcClientOnlyWhereTheServiceNamesIt() {
    noClasses()
        .that(
            new DescribedPredicate<>("are not named in jdbcClientAllowed") {
              @Override
              public boolean test(JavaClass javaClass) {
                return !jdbcClientAllowed.contains(javaClass.getName());
              }
            })
        .should()
        .dependOnClassesThat()
        .haveFullyQualifiedName("org.springframework.jdbc.core.simple.JdbcClient")
        .because(
            "raw SQL escapes the @Query guardrails and Hibernate flush coordination; a class that"
                + " needs it is named in service-conventions.json with the reason in its own comment")
        .check(appClasses);
  }

  @Test
  void onlyPersistenceDependsOnJpa() {
    noClasses()
        .that()
        .resideOutsideOfPackage(root + ".persistence..")
        .should()
        .dependOnClassesThat(
            new DescribedPredicate<>("belong to jakarta.persistence or Hibernate") {
              @Override
              public boolean test(JavaClass javaClass) {
                String name = javaClass.getPackageName();
                return name.startsWith("jakarta.persistence") || name.startsWith("org.hibernate");
              }
            })
        .because("everything but persistence is storage-agnostic; JPA is a persistence detail")
        .check(appClasses);
  }

  private static String field(String pin, String name) {
    Matcher m = Pattern.compile("\"" + name + "\"\\s*:\\s*\"([^\"]*)\"").matcher(pin);
    if (!m.find()) {
      throw new IllegalStateException("service-conventions.json has no \"" + name + "\"");
    }
    return m.group(1);
  }

  private static List<String> list(String pin, String name) {
    List<String> values = new ArrayList<>();
    Matcher m = Pattern.compile("\"" + name + "\"\\s*:\\s*\\[([^\\]]*)\\]").matcher(pin);
    if (m.find()) {
      Matcher v = Pattern.compile("\"([^\"]*)\"").matcher(m.group(1));
      while (v.find()) {
        values.add(v.group(1));
      }
    }
    return values;
  }
}
