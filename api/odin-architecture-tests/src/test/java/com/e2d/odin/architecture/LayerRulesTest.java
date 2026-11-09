package com.e2d.odin.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

class LayerRulesTest {

    private static final JavaClasses ODIN = new ClassFileImporter().importPackages("com.e2d.odin");

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
                .allowEmptyShould(true)
                .check(ODIN);
    }

    @Test
    void noSpringOutsideAdaptersAndBootstrap() {
        noClasses()
                .that().resideOutsideOfPackages("com.e2d.odin.adapter..", "com.e2d.odin.bootstrap..")
                .should().dependOnClassesThat().resideInAPackage("org.springframework..")
                .check(ODIN);
    }
}
