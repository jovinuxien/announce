package org.linlinjava.litemall.elastic.dto.elasticdto;

import java.util.List;

public class SearchData {
    private String fullTextSearch;
    private String fulltextBoosted;
    private List<StringFacet> stringFacets;
    private List<NumberFacet> numberFacets;
}
