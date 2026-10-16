package lt.baltic.gateway.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.GeneralCodingRules;

/** Enforces the modular-monolith boundaries described in docs/adr/0001-modular-monolith.md. */
@AnalyzeClasses(packages = "lt.baltic.gateway", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {
    private static final String[] FEATURES = {"identity", "accounts", "payments", "audit"};

    @ArchTest
    static final ArchRule featuresHaveNoCyclicDependencies =
            slices().matching("lt.baltic.gateway.(*)..").should().beFreeOfCycles();

    @ArchTest
    static final ArchRule platformDoesNotDependOnFeatures =
            noClasses().that().resideInAPackage("..platform..")
                    .should().dependOnClassesThat().resideInAnyPackage("..identity..", "..accounts..", "..payments..", "..audit..");

    @ArchTest
    static final ArchRule domainIsIndependentOfOuterLayers =
            noClasses().that().resideInAPackage("..domain..")
                    .should().dependOnClassesThat().resideInAnyPackage("..api..", "..application..", "..infrastructure..");

    @ArchTest
    static final ArchRule applicationDoesNotDependOnApi =
            noClasses().that().resideInAPackage("..application..").should().dependOnClassesThat().resideInAPackage("..api..");

    @ArchTest
    static final ArchRule apiDoesNotTouchPersistenceDirectly =
            noClasses().that().resideInAPackage("..api..").should().dependOnClassesThat().resideInAPackage("..infrastructure..");

    @ArchTest
    static void featuresExposeOnlyApplicationAndDomainToOtherFeatures(JavaClasses classes) {
        for (String feature : FEATURES) {
            noClasses().that().resideOutsideOfPackage("..%s..".formatted(feature))
                    .should().dependOnClassesThat().resideInAnyPackage("..%s.api..".formatted(feature), "..%s.infrastructure..".formatted(feature))
                    .because("%s must only be used through its application and domain packages".formatted(feature))
                    .check(classes);
        }
    }

    @ArchTest
    static final ArchRule noFieldInjection = GeneralCodingRules.NO_CLASSES_SHOULD_USE_FIELD_INJECTION;

    @ArchTest
    static final ArchRule noJavaUtilLogging = GeneralCodingRules.NO_CLASSES_SHOULD_USE_JAVA_UTIL_LOGGING;

    @ArchTest
    static final ArchRule noGenericExceptions = GeneralCodingRules.NO_CLASSES_SHOULD_THROW_GENERIC_EXCEPTIONS;
}
