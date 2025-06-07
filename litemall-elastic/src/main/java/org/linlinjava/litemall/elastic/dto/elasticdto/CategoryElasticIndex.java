package org.linlinjava.litemall.elastic.dto.elasticdto;

import java.util.List;

public class CategoryElasticIndex {
    private List<String> directParents;
    private List<String> allParents;
    private List<String> paths;
}
