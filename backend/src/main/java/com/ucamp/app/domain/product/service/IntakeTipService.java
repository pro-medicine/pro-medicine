package com.ucamp.app.domain.product.service;

import com.ucamp.app.domain.product.dto.IntakeTipResponse;
import com.ucamp.app.domain.product.entity.CatalogProduct;
import com.ucamp.app.domain.product.entity.Supplement;
import com.ucamp.app.domain.product.repository.CatalogProductRepository;
import com.ucamp.app.domain.product.repository.SupplementRepository;
import com.ucamp.app.global.exception.BusinessException;
import com.ucamp.app.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class IntakeTipService {

    private record Rule(
            String ingredient,
            List<String> keywords,
            List<String> summaryPrefixes,
            String message) {
    }

    private static final List<Rule> RULES = List.of(
            new Rule("비타민 C", List.of("비타민C"), List.of(),
                    "비타민 C는 아침 또는 점심 식후에 먹는 것이 좋아요."),
            new Rule("비타민 B군", List.of("비타민B"), List.of(),
                    "비타민 B군은 아침 또는 점심 식후에 먹는 것이 좋아요."),
            new Rule("비타민 D", List.of("비타민D"), List.of(),
                    "비타민 D는 지용성이라 식사와 함께(식후) 먹어야 흡수가 잘 돼요."),
            new Rule("비타민 A", List.of("비타민A"), List.of(),
                    "비타민 A는 지용성이라 식후에 먹는 것이 좋아요."),
            new Rule("비타민 E", List.of("비타민E"), List.of(),
                    "비타민 E는 지용성이라 식후에 먹는 것이 좋아요."),
            new Rule("오메가3", List.of("오메가3", "EPA", "DHA"), List.of(),
                    "오메가3는 식사 직후에 먹으면 흡수가 잘 되고 속이 편해요."),
            new Rule("철분", List.of("철분"), List.of("철"),
                    "철분은 공복에 흡수가 잘 되지만 속이 불편하면 식후에 드세요. 커피·차·칼슘제와는 시간 간격을 두세요."),
            new Rule("칼슘", List.of("칼슘"), List.of(),
                    "칼슘은 식후에 먹는 것이 좋아요. 철분제와는 시간 간격을 두세요."),
            new Rule("마그네슘", List.of("마그네슘"), List.of(),
                    "마그네슘은 저녁에 먹는 것이 좋아요. 속이 불편하면 식후에 드세요."),
            new Rule("아연", List.of("아연"), List.of(),
                    "아연은 식후에 먹으면 속이 편해요. 철분·칼슘제와는 시간 간격을 두세요.")
    );

    private final CatalogProductRepository catalogProductRepository;
    private final SupplementRepository supplementRepository;

    public List<IntakeTipResponse> getTips(Long catalogProductId) {
        CatalogProduct product = catalogProductRepository.findById(catalogProductId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
        return buildTips(product.getProductName(), product.getIngredientSummary());
    }

    public List<IntakeTipResponse> getSupplementTips(Long supplementId) {
        Supplement s = supplementRepository.findById(supplementId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
        return buildCategoryTip(s.getCategory());
    }

    private List<IntakeTipResponse> buildCategoryTip(String category) {
        String normalizedCategory = normalize(category);

        if (normalizedCategory.isBlank()) {
            return List.of();
        }

        return RULES.stream()
                .filter(rule -> {
                    String ingredient = normalize(rule.ingredient());
                    if (ingredient.equals(normalizedCategory)) {
                        return true;
                    }
                    boolean keywordMatch = rule.keywords().stream()
                            .map(this::normalize)
                            .anyMatch(normalizedCategory::equals);
                    if (keywordMatch) {
                        return true;
                    }
                    boolean prefixMatch = rule.summaryPrefixes().stream()
                            .map(this::normalize)
                            .anyMatch(normalizedCategory::equals);
                    if (prefixMatch) {
                        return true;
                    }
                    return ingredient.equals("오메가3")
                            && normalizedCategory.contains("EPA")
                            && normalizedCategory.contains("DHA");
                })
                .findFirst()
                .map(rule -> List.of(
                        new IntakeTipResponse(rule.ingredient(), rule.message())
                ))
                .orElseGet(List::of);
    }

    private List<IntakeTipResponse> buildTips(String productName, String ingredientSummary) {
        String name = normalize(productName);
        String summary = nvl(ingredientSummary);
        String haystack = name + " " + normalize(summary);

        List<String> parts = Arrays.stream(summary.split(","))
                .map(this::normalize)
                .toList();

        List<IntakeTipResponse> tips = new ArrayList<>();
        for (Rule rule : RULES) {
            boolean byKeyword = rule.keywords().stream().anyMatch(haystack::contains);
            boolean byPrefix = rule.summaryPrefixes().stream()
                    .anyMatch(prefix -> parts.stream().anyMatch(part -> startsWithIngredient(part, prefix)));
            if (byKeyword || byPrefix) {
                tips.add(new IntakeTipResponse(rule.ingredient(), rule.message()));
            }
        }
        return tips;
    }

    private String normalize(String value) {
        return nvl(value).replace(" ", "").replace("-", "");
    }

    private String nvl(String value) {
        return value == null ? "" : value;
    }

    private static boolean startsWithIngredient(String part, String prefix) {
        if (!part.startsWith(prefix)) {
            return false;
        }
        return part.length() == prefix.length() || !Character.isLetter(part.charAt(prefix.length()));
    }
}