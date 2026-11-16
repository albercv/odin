package com.e2d.odin.architecture;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideOutsideOfPackages;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

class LayerRulesTest {

    private static final JavaClasses ODIN = new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests())
            .importPackages("com.e2d.odin");

    @Test
    void domainDependsOnlyOnJava() {
        classes()
                .that().resideInAPackage("com.e2d.odin.domain..")
                .should().onlyDependOnClassesThat().resideInAnyPackage("com.e2d.odin.domain..", "java..")
                .check(ODIN);
    }

    @Test
    void applicationDependsOnlyOnDomainAndJava() {
        classes()
                .that().resideInAPackage("com.e2d.odin.application..")
                .should().onlyDependOnClassesThat()
                .resideInAnyPackage("com.e2d.odin.application..", "com.e2d.odin.domain..", "java..")
                .check(ODIN);
    }

    @Test
    void adaptersDoNotDependOnEachOther() {
        slices()
                .matching("com.e2d.odin.adapter.(*)..")
                .should().notDependOnEachOther()
                .check(ODIN);
    }

    @Test
    void noSpringOutsideAdaptersAndBootstrap() {
        noClasses()
                .that().resideOutsideOfPackages("com.e2d.odin.adapter..", "com.e2d.odin.bootstrap..")
                .should().dependOnClassesThat().resideInAPackage("org.springframework..")
                .check(ODIN);
    }

    @Test
    void nobodyDependsOnBootstrapExceptBootstrap() {
        noClasses()
                .that().resideOutsideOfPackage("com.e2d.odin.bootstrap..")
                .should().dependOnClassesThat().resideInAPackage("com.e2d.odin.bootstrap..")
                .check(ODIN);
    }

    @Test
    void drivenAdaptersUseOnlyPortsAndSharedOfApplication() {
        noClasses()
                .that().resideInAnyPackage(
                        "com.e2d.odin.adapter.postgres..",
                        "com.e2d.odin.adapter.filestore..",
                        "com.e2d.odin.adapter.readers..",
                        "com.e2d.odin.adapter.readerservice..",
                        "com.e2d.odin.adapter.taggerollama..")
                .should().dependOnClassesThat(
                        resideInAPackage("com.e2d.odin.application..")
                                .and(resideOutsideOfPackages(
                                        "com.e2d.odin.application.port..",
                                        "com.e2d.odin.application.shared..")))
                .check(ODIN);
    }

    @Test
    void applicationPackagesHaveNoCycles() {
        slices().matching("com.e2d.odin.application.(*)..").should().beFreeOfCycles().check(ODIN);
    }

    @Test
    void domainPackagesHaveNoCycles() {
        slices().matching("com.e2d.odin.domain.(*)..").should().beFreeOfCycles().check(ODIN);
    }
}
