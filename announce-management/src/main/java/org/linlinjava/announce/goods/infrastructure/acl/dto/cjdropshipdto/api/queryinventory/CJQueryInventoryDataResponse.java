package org.linlinjava.announce.goods.infrastructure.acl.dto.cjdropshipdto.api.queryinventory;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import org.linlinjava.announce.goods.infrastructure.acl.dto.cjdropshipdto.api.inventory.CJInventoryData;

import java.util.List;

@Data
public class CJQueryInventoryDataResponse {
    @JsonProperty("code")
    private int code; // Response code, e.g., 200
    @JsonProperty("result")
    private boolean result; // Success or failure, e.g., true
    @JsonProperty("message")
    private String message; // Response message, e.g., "Success"
    @JsonProperty("data")
    private List<CJInventoryData> data; // Product data
    @JsonProperty("requestId")
    private String requestId; //
}
