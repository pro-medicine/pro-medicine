package com.ucamp.app.domain.product.dto;

public record CatalogImportResponse(
        int fetched,
        int created,
        int updated,
        int skipped
) {
}
