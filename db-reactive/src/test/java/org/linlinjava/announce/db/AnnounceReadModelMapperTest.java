package org.linlinjava.announce.db;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.linlinjava.announce.db.dao.reactive.AnnounceBaseMapper;
import org.linlinjava.announce.db.dao.reactive.AnnounceEventDetailMapper;
import org.linlinjava.announce.db.dao.reactive.AnnounceGoodsDetailMapper;
import org.linlinjava.announce.db.dao.reactive.AnnounceJobDetailMapper;
import org.linlinjava.announce.db.domain.AnnounceBase;
import org.linlinjava.announce.db.domain.AnnounceBaseExample;
import org.linlinjava.announce.db.domain.AnnounceEventDetail;
import org.linlinjava.announce.db.domain.AnnounceEventDetailExample;
import org.linlinjava.announce.db.domain.AnnounceGoodsDetail;
import org.linlinjava.announce.db.domain.AnnounceGoodsDetailExample;
import org.linlinjava.announce.db.domain.AnnounceJobDetail;
import org.linlinjava.announce.db.domain.AnnounceJobDetailExample;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The announce read model: {@code announce} (JSON array column {@code gallery}, BLOB column {@code detail}, ids chosen
 * by the caller) and its per-kind detail tables, written with the idempotent {@code upsert} statements the projections use.
 */
@SpringBootTest
class AnnounceReadModelMapperTest {

    private static final String PREFIX = "it-announce-";
    private static final LocalDateTime NOW = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);

    @Autowired private AnnounceBaseMapper announces;
    @Autowired private AnnounceGoodsDetailMapper goods;
    @Autowired private AnnounceJobDetailMapper jobs;
    @Autowired private AnnounceEventDetailMapper events;

    @AfterEach
    void cleanUp() {
        AnnounceGoodsDetailExample g = new AnnounceGoodsDetailExample();
        g.or().andAnnounceIdLike(PREFIX + "%");
        goods.deleteByExample(g).block();
        AnnounceJobDetailExample j = new AnnounceJobDetailExample();
        j.or().andAnnounceIdLike(PREFIX + "%");
        jobs.deleteByExample(j).block();
        AnnounceEventDetailExample e = new AnnounceEventDetailExample();
        e.or().andAnnounceIdLike(PREFIX + "%");
        events.deleteByExample(e).block();
        AnnounceBaseExample a = new AnnounceBaseExample();
        a.or().andIdLike(PREFIX + "%");
        announces.deleteByExample(a).block();
    }

    @Test
    void seededGoodsRowsAreKindGoodsWithGalleryAndDetailRow() {
        AnnounceBaseExample example = new AnnounceBaseExample();
        example.or().andKindEqualTo("GOODS").andDeletedEqualTo(false).andGalleryIsNotNull();
        example.setOrderByClause("id asc");
        StepVerifier.create(announces.selectPageByExample(example, 0, 1))
                .assertNext(a -> {
                    assertNotNull(a.getGallery());
                    assertTrue(a.getGallery()[0].startsWith("http"));
                    assertNull(a.getDetail(), "detail is a BLOB column and must not be loaded by the base result map");
                    assertEquals(1, a.getSellerId(), "migrated rows belong to the seeded member");
                    StepVerifier.create(goods.selectByPrimaryKey(a.getId()).map(AnnounceGoodsDetail::getSaleType))
                            .expectNext("FIXED_PRICE").verifyComplete();
                })
                .verifyComplete();
    }

    @Test
    void upsertInsertsThenOverwritesTheGoodsAnnounce() {
        String id = PREFIX + System.nanoTime();
        AnnounceBase a = base(id, "GOODS", "lamp");
        a.setGallery(new String[]{"http://example.invalid/a.jpg", "http://example.invalid/b.jpg"});
        a.setDetail("<p>rich text</p>");
        a.setRegionId(1);
        a.setCity("Stockholm");
        a.setPostalCode("11122");
        a.setLatitude(new BigDecimal("59.329323"));
        a.setLongitude(new BigDecimal("18.068581"));
        AnnounceGoodsDetail d = new AnnounceGoodsDetail();
        d.setAnnounceId(id);
        d.setPrice(new BigDecimal("8.50"));
        d.setCondition("GOOD");
        d.setSaleType("FIXED_PRICE");
        d.setUnit("pcs");

        StepVerifier.create(announces.upsert(a)).expectNext(1).verifyComplete();
        StepVerifier.create(goods.upsert(d)).expectNext(1).verifyComplete();

        AnnounceBaseExample byId = new AnnounceBaseExample();
        byId.or().andIdEqualTo(id);
        StepVerifier.create(announces.selectOneByExampleWithBLOBs(byId))
                .assertNext(found -> {
                    assertArrayEquals(a.getGallery(), found.getGallery());
                    assertEquals("<p>rich text</p>", found.getDetail());
                    assertEquals("DRAFT", found.getStatus());
                    assertEquals("Stockholm", found.getCity());
                    assertEquals(0, new BigDecimal("59.329323").compareTo(found.getLatitude()));
                })
                .verifyComplete();

        // replaying the same subject overwrites the row instead of failing on the primary key
        a.setStatus("PUBLISHED");
        a.setPublishedAt(NOW);
        a.setExpiresAt(NOW.plusDays(60));
        a.setGallery(new String[]{"http://example.invalid/c.jpg"});
        a.setLastEventId("evt-2");
        d.setPrice(new BigDecimal("7.00"));
        StepVerifier.create(announces.upsert(a)).expectNext(2).verifyComplete();   // MySQL reports 2 for an updated row
        StepVerifier.create(goods.upsert(d)).expectNext(2).verifyComplete();
        StepVerifier.create(announces.selectByPrimaryKey(id).map(x -> x.getGallery().length + ":" + x.getStatus() + ":" + x.getLastEventId()))
                .expectNext("1:PUBLISHED:evt-2").verifyComplete();
        StepVerifier.create(goods.selectByPrimaryKey(id).map(x -> x.getPrice().compareTo(new BigDecimal("7.00"))))
                .expectNext(0).verifyComplete();

        StepVerifier.create(announces.selectByExampleSelective(byId, AnnounceBase.Column.id, AnnounceBase.Column.title))
                .assertNext(x -> {
                    assertEquals(a.getTitle(), x.getTitle());
                    assertNull(x.getGallery());
                })
                .verifyComplete();
    }

    @Test
    void jobAndEventDetailsRoundTrip() {
        String jobId = PREFIX + "job-" + System.nanoTime();
        StepVerifier.create(announces.upsert(base(jobId, "JOB", "Java developer"))).expectNext(1).verifyComplete();
        AnnounceJobDetail job = new AnnounceJobDetail();
        job.setAnnounceId(jobId);
        job.setEmployer("Acme");
        job.setContractType("FULL_TIME");
        job.setSalaryMin(new BigDecimal("40000"));
        job.setSalaryMax(new BigDecimal("55000"));
        job.setCurrency("SEK");
        job.setApplicationDeadline(NOW.plusDays(30));
        job.setApplyUrlOrEmail("jobs@acme.invalid");
        StepVerifier.create(jobs.upsert(job)).expectNext(1).verifyComplete();
        StepVerifier.create(jobs.selectByPrimaryKey(jobId).map(j -> j.getEmployer() + ":" + j.getCurrency() + ":" + j.getApplicationDeadline()))
                .expectNext("Acme:SEK:" + NOW.plusDays(30)).verifyComplete();

        String eventId = PREFIX + "event-" + System.nanoTime();
        StepVerifier.create(announces.upsert(base(eventId, "EVENT", "Flea market"))).expectNext(1).verifyComplete();
        AnnounceEventDetail event = new AnnounceEventDetail();
        event.setAnnounceId(eventId);
        event.setStartsAt(NOW.plusDays(3));
        event.setEndsAt(NOW.plusDays(3).plusHours(6));
        event.setVenue("Town square");
        event.setOrganizer("The city");
        event.setTicketed(true);
        event.setTicketPrice(new BigDecimal("5.00"));
        event.setCapacity(200);
        StepVerifier.create(events.upsert(event)).expectNext(1).verifyComplete();
        StepVerifier.create(events.selectByPrimaryKey(eventId).map(e -> e.getVenue() + ":" + e.getTicketed() + ":" + e.getCapacity()))
                .expectNext("Town square:true:200").verifyComplete();

        AnnounceBaseExample byKind = new AnnounceBaseExample();
        byKind.or().andKindEqualTo("EVENT").andIdLike(PREFIX + "%");
        StepVerifier.create(announces.countByExample(byKind)).expectNext(1L).verifyComplete();
    }

    private static AnnounceBase base(String id, String kind, String title) {
        AnnounceBase a = new AnnounceBase();
        a.setId(id);
        a.setKind(kind);
        a.setSellerId(1);
        a.setCategoryId(1005000);
        a.setTitle(PREFIX + title);
        a.setBrief("integration test");
        a.setKeywords("test");
        a.setStatus("DRAFT");
        a.setAddTime(NOW);
        a.setUpdateTime(NOW);
        a.setDeleted(false);
        return a;
    }
}
