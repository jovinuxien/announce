package org.linlinjava.announce.goods;

import org.linlinjava.announce.goods.config.AsyncSyncConfiguration;
import org.linlinjava.announce.goods.config.EmbeddedSQL;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
//@SpringBootTest(classes = { AnnounceApplication.class, AsyncSyncConfiguration.class, TestSecurityConfiguration.class })
@SpringBootTest(classes = { AnnounceGoodsManagement.class, AsyncSyncConfiguration.class })
//@EmbeddedKafka
@EmbeddedSQL
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public @interface IntegrationTest {
}
