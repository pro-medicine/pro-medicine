package com.ucamp.app.domain.product.controller;

import com.ucamp.app.domain.product.dto.CatalogImportResponse;
import com.ucamp.app.domain.product.service.CatalogProductImportService;
import com.ucamp.app.global.common.ApiResponse;
import com.ucamp.app.global.exception.BusinessException;
import com.ucamp.app.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/catalog-products")
public class CatalogProductImportController {
    private final CatalogProductImportService catalogProductImportService;

    @PostMapping("/import")
    public ResponseEntity<ApiResponse<CatalogImportResponse>>
    importPage(
            @RequestParam(defaultValue = "1") int fromPage,
            @RequestParam(defaultValue = "1") int toPage,
            @RequestParam(defaultValue = "100") int numOfRows) {
        if (fromPage < 1 || toPage < fromPage || toPage - fromPage >= 100) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        int fetched = 0;
        int created = 0;
        int updated = 0;
        int skipped = 0;
        for(int page = fromPage; page <= toPage; page++) {
            CatalogImportResponse result = catalogProductImportService.importPage(page, numOfRows);
            fetched += result.fetched();
            created += result.created();
            updated += result.updated();
            skipped += result.skipped();
        }
                return ResponseEntity.ok(ApiResponse.success(new CatalogImportResponse(fetched, created, updated, skipped)));
    }

}
