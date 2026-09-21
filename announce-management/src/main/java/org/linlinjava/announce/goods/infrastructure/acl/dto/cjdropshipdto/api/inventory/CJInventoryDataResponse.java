package org.linlinjava.announce.goods.infrastructure.acl.dto.cjdropshipdto.api.inventory;

import lombok.Data;

import java.util.List;

@Data
public class CJInventoryDataResponse {
    private int code; // Response code, e.g., 200
    private boolean result; // Success or failure, e.g., true
    private String message; // Response message, e.g., "Success"
    private List<CJInventoryData> data; // Product data
    private String requestId; //
}
