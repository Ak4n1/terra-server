package com.terra.api.auth.api.dto;

import java.util.List;

public record AccountActivityListResponse(
        List<AccountActivityEntryResponse> items,
        boolean hasMore,
        int page,
        int size
) {
}
