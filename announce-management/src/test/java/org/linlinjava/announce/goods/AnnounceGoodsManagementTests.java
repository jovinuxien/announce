package org.linlinjava.announce.goods;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.linlinjava.announce.goods.config.AbstractMysqlTestBase;
import org.linlinjava.announce.goods.domain.entities.AnnounceCategoryEntity;
import org.linlinjava.announce.goods.domain.model.repositories.AnnounceCategoryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import static org.junit.Assert.*;

//@SpringJUnitConfig(classes = AnnounceGoodsManagement.class)
//@ActiveProfiles("test")
@SpringBootTest
@ActiveProfiles("test")
public class AnnounceGoodsManagementTests extends AbstractMysqlTestBase {


    @Autowired
    ApplicationContext context;

    @Autowired
    private AnnounceCategoryRepository categoryRepository;

    WebTestClient client;

    @BeforeEach
    public void setup() {
        this.client = WebTestClient
                .bindToApplicationContext(this.context)
                .configureClient()
                .build();
    }

    @Test
    void contextLoads() {
        //assertTrue(true);
        assertTrue(categoryRepository != null);
        // TODO: Add more tests here as per your requirements
    }

    @Test
    void testQueryL1Categories() {
        // Assuming you have some L1 categories in your test database
        Flux<AnnounceCategoryEntity> categoryFlux = categoryRepository.queryL1();

        StepVerifier.create(categoryFlux)
                .expectNextMatches(category -> {
                    assertNotNull(category.getId());
                    assertEquals("0", category.getLevel());
                    return true;
                })
                .thenCancel() // We only check the first item and then cancel the subscription
                .verify();
    }

    @Test
    void testFindById() {
        // Assuming a category with ID 1005000 exists in your test database
        //Integer existingCategoryId = 1005000;
        Integer existingCategoryId = 00;

        StepVerifier.create(categoryRepository.findByIdAndDeletedFalse(existingCategoryId))
                .assertNext(category -> {
                    assertEquals(existingCategoryId, category.getId());
                    assertNotNull(category.getName());
                })
                .verifyComplete();
    }
}
