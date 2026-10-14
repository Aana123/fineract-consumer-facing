/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */


package org.apache.fineract.consumer;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.apache.fineract.consumer.ArchitectureFixtures.BASE_PACKAGE;
import static org.apache.fineract.consumer.ArchitectureFixtures.INFRASTRUCTURE;
import static org.apache.fineract.consumer.ArchitectureFixtures.MAIN_CLASSES;
import static org.apache.fineract.consumer.ArchitectureFixtures.side;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.data.repository.Repository;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Naming rules from AGENTS.md "Backend conventions". */
class NamingArchitectureTest {

    /**
     * Top-level data/ classes except enums (contract enums carry no role suffix), {@code *Constants} holders and
     * {@code @ConfigurationProperties} binders, which hold no request or response data.
     */
    private static final DescribedPredicate<JavaClass> ROLE_CARRYING_DATA_CLASSES = DescribedPredicate.describe(
            "are role-carrying data classes (top level, not enums, *Constants or @ConfigurationProperties)",
            javaClass -> javaClass.isTopLevelClass() && !javaClass.isEnum() && !javaClass.getSimpleName().endsWith("Constants")
                    && !javaClass.isAnnotatedWith(ConfigurationProperties.class));

    @Test
    void commandDataClassesCarryACommandRoleSuffix() {
        classes().that().resideInAPackage(side("command", "data")).and(ROLE_CARRYING_DATA_CLASSES).should()
                .haveSimpleNameEndingWith("Command").orShould().haveSimpleNameEndingWith("CommandData").orShould()
                .haveSimpleNameEndingWith("CommandRequest").orShould().haveSimpleNameEndingWith("CommandResponse")
                .as("command-side data classes end in Command, CommandData, CommandRequest or CommandResponse")
                .because("AGENTS.md Naming: classes under data/ carry a role suffix plus the CQRS side").check(MAIN_CLASSES);
    }

    @Test
    void queryDataClassesCarryAQueryRoleSuffix() {
        classes().that().resideInAPackage(side("query", "data")).and(ROLE_CARRYING_DATA_CLASSES).should()
                .haveSimpleNameEndingWith("Query").orShould().haveSimpleNameEndingWith("QueryData").orShould()
                .haveSimpleNameEndingWith("QueryRequest").orShould().haveSimpleNameEndingWith("QueryResponse")
                .because("AGENTS.md Naming: classes under data/ carry a role suffix plus the CQRS side").check(MAIN_CLASSES);
    }

    @Test
    void noForbiddenDataSuffixes() {
        noClasses().that().resideInAPackage(BASE_PACKAGE + "..").and().areNotAnonymousClasses().and().areNotLocalClasses()
                .should().haveSimpleNameEndingWith("Dto").orShould().haveSimpleNameEndingWith("DTO").orShould()
                .haveSimpleNameEndingWith("Result").orShould().haveSimpleNameEndingWith("Summary").orShould()
                .haveSimpleNameEndingWith("Info").orShould().haveSimpleNameEndingWith("View")
                .because("AGENTS.md Naming: do not introduce *Dto, *Result, *Summary, *Info, *View").check(MAIN_CLASSES);
    }

    @Test
    void infrastructureClassesAreNamedByBehavioralRole() {
        noClasses().that().resideInAPackage(INFRASTRUCTURE).and().areNotAnonymousClasses().and().areNotLocalClasses()
                .should().haveSimpleNameEndingWith("Manager").orShould().haveSimpleNameEndingWith("Util").orShould()
                .haveSimpleNameEndingWith("Utils").orShould().haveSimpleNameEndingWith("Context")
                .because("AGENTS.md Naming: infrastructure classes are never *Manager, *Util, *Context").check(MAIN_CLASSES);
    }

    @Test
    void commandControllersAreNamedCommandController() {
        classes().that().resideInAPackage(side("command", "api")).and().areAnnotatedWith(RestController.class).should()
                .haveSimpleNameEndingWith("CommandController")
                .because("AGENTS.md CQRS layout: command/api/ holds <Feature>CommandController").check(MAIN_CLASSES);
    }

    @Test
    void queryControllersAreNamedQueryController() {
        classes().that().resideInAPackage(side("query", "api")).and().areAnnotatedWith(RestController.class).should()
                .haveSimpleNameEndingWith("QueryController")
                .because("AGENTS.md CQRS layout: query/api/ holds <Feature>QueryController").check(MAIN_CLASSES);
    }

    @Test
    void commandRepositoriesAreNamedCommandRepository() {
        classes().that().resideInAPackage(side("command")).and().areAssignableTo(Repository.class).should()
                .haveSimpleNameEndingWith("CommandRepository")
                .because("AGENTS.md CQRS layout: command/repository/ holds <Feature>CommandRepository").check(MAIN_CLASSES);
    }

    @Test
    void queryRepositoriesAreNamedQueryRepository() {
        classes().that().resideInAPackage(side("query")).and().areAssignableTo(Repository.class).should()
                .haveSimpleNameEndingWith("QueryRepository")
                .because("AGENTS.md CQRS layout: each side owns its repositories, named per side").check(MAIN_CLASSES);
    }

    @Test
    void queryEntitiesAreNamedQueryEntity() {
        classes().that().resideInAPackage(side("query")).and().areAnnotatedWith(jakarta.persistence.Entity.class).should()
                .haveSimpleNameEndingWith("QueryEntity")
                .because("AGENTS.md CQRS layout: the query side maps the table with its own <Feature>QueryEntity").check(MAIN_CLASSES);
    }

    @Test
    void exceptionHandlersAreNamedHandler() {
        classes().that().areAnnotatedWith(RestControllerAdvice.class).or().areAnnotatedWith(ControllerAdvice.class).should()
                .haveSimpleNameEndingWith("Handler")
                .because("AGENTS.md API error contract: handlers are named *Handler, not *Mapper").check(MAIN_CLASSES);
    }
}
