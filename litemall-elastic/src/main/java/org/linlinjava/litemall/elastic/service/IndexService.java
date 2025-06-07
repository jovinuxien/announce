package org.linlinjava.litemall.elastic.service;


import org.linlinjava.litemall.db.dao.LitemallElasticMapper;
import org.linlinjava.litemall.db.dto.ElasticDto;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class IndexService {

    private  LitemallElasticMapper elasticMapper;

    public IndexService(LitemallElasticMapper mapper){
        this.elasticMapper = mapper;
    }
    public  Map<String, Object> getGoods(String manufacturerName, String material){
        List<ElasticDto> data = elasticMapper.selectGoodsWithJoin(manufacturerName, material);
        Map<String, Object> entity = new HashMap<>();
        entity.put("list", data);
        entity.put("fetched time", System.currentTimeMillis());
        return entity;
    }
}
