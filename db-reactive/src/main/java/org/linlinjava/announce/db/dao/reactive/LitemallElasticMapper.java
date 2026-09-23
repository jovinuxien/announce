package org.linlinjava.announce.db.dao.reactive;

import org.apache.ibatis.annotations.Param;
import org.linlinjava.announce.db.dto.ElasticDto;
import reactor.core.publisher.Flux;

/** Flattened goods announces for the search index (join over announce, goods detail, brand, category, attribute). */
public interface LitemallElasticMapper {
    Flux<ElasticDto> selectGoodsWithJoin(@Param("manufacturerName") String manufacturerName,
                                         @Param("attribute") String attribute);
}
