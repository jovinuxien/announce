package org.linlinjava.litemall.elastic.dto;

import org.linlinjava.litemall.elastic.dto.elasticdto.*;

import java.util.List;

public class DataFormatForElasticIndexDto {
    private String type;
    private SearchResultData searchDataResult;
    private SearchData searchData;
    private List<String> completionTerms;
    private List<String> suggestionsTerms;
    private Integer numberSort;
    private String stringSort;
    private Scores scores;
    private CategoryElasticIndex category;
    private CategoryScores categoryScores;
}
