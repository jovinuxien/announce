package org.linlinjava.announce;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

/**
 * The four package roots are bounded contexts that share nothing but the public events of {@code order-api}
 * ({@code doc/order-context.md} §2): no root depends on another root, none on the cross-cutting {@code orderservice}
 * package, and no cycles between the slices. {@code org.linlinjava.announce.order.api} is the contract module, not the
 * order root, hence the explicit exception.
 */
@AnalyzeClasses(packages = "org.linlinjava.announce", importOptions = ImportOption.DoNotIncludeJars.class)
class ContextBoundariesTest {

    private static final String AUCTION = "org.linlinjava.announce.auction..";
    private static final String ORDER = "org.linlinjava.announce.order..";
    private static final String ORDER_API = "org.linlinjava.announce.order.api..";
    private static final String PAYMENT = "org.linlinjava.announce.payment..";
    private static final String SHIPPING = "org.linlinjava.announce.shipping..";
    private static final String SERVICE = "org.linlinjava.announce.orderservice..";

    /** The order root without its contract package. */
    private static final DescribedPredicate<JavaClass> ORDER_ROOT = JavaClass.Predicates.resideInAPackage(ORDER)
            .and(DescribedPredicate.not(JavaClass.Predicates.resideInAPackage(ORDER_API))).as("the order context (not order.api)");

    @ArchTest
    static final ArchRule auction_shares_nothing = noClasses().that().resideInAPackage(AUCTION)
            .should().dependOnClassesThat().resideInAnyPackage(PAYMENT, SHIPPING, SERVICE)
            .orShould().dependOnClassesThat(ORDER_ROOT);

    @ArchTest
    static final ArchRule order_shares_nothing = noClasses().that().resideInAPackage(ORDER).and().resideOutsideOfPackage(ORDER_API)
            .should().dependOnClassesThat().resideInAnyPackage(AUCTION, PAYMENT, SHIPPING, SERVICE);

    @ArchTest
    static final ArchRule payment_shares_nothing = noClasses().that().resideInAPackage(PAYMENT)
            .should().dependOnClassesThat().resideInAnyPackage(AUCTION, SHIPPING, SERVICE)
            .orShould().dependOnClassesThat(ORDER_ROOT);

    @ArchTest
    static final ArchRule shipping_shares_nothing = noClasses().that().resideInAPackage(SHIPPING)
            .should().dependOnClassesThat().resideInAnyPackage(AUCTION, PAYMENT, SERVICE)
            .orShould().dependOnClassesThat(ORDER_ROOT);

    @ArchTest
    static final ArchRule contracts_depend_on_no_context = noClasses().that().resideInAnyPackage(ORDER_API, "org.linlinjava.announce.messaging.api..")
            .should().dependOnClassesThat().resideInAnyPackage(AUCTION, PAYMENT, SHIPPING, SERVICE)
            .orShould().dependOnClassesThat(ORDER_ROOT).allowEmptyShould(true);   // the contracts live in jars (order-api); see DoNotIncludeJars

    @ArchTest
    static final ArchRule no_cycles = slices().matching("org.linlinjava.announce.(*)..").should().beFreeOfCycles();
}
