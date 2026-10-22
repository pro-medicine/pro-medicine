package com.ucamp.app.eval;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import com.ucamp.app.eval.EvalCaseFile.Dose;
import com.ucamp.app.eval.EvalCaseFile.EvalCase;
import com.ucamp.app.infra.llm.FakeLlmClient;
import com.ucamp.app.infra.llm.FakeLlmClient.FakeAnswer;
import com.ucamp.app.infra.llm.LlmMessage;
import com.ucamp.app.infra.llm.LlmRequest;
import com.ucamp.app.infra.llm.LlmResponse;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 평가 러너 검증용 가짜 LLM 응답. 실제 LLM 품질과 무관하다 (results.csv label에 fake를 붙인다).
 * - oracle: 기대값을 그대로 돌려준다 → recall 1, violation 0, dose_error 0이 나와야 러너가 맞다
 * - noisy: 시드 고정 난수로 일부러 틀린다 (형식 깨짐, 금지 성분 포함, 필수 성분 누락, 용량 오차).
 *   V3·V4의 규칙 검증이 위반을 걸러내는지 같은 실수 패턴으로 확인하는 용도. 프롬프트에 금지 목록이 있으면
 *   금지 성분을 덜 넣도록(40% → 5%) 흉내 낸다.
 */
public final class FakeScenarios {
    private FakeScenarios() {
    }

    public static FakeAnswer oracle(EvalCase c, IngredientAliases aliases) {
        Set<String> codes = new LinkedHashSet<>(c.expected().mustInclude());
        List<String> may = c.expected().mayInclude();
        int extra = codes.isEmpty() ? 2 : 1;
        for (int i = 0; i < Math.min(extra, may.size()); i++) {
            codes.add(may.get(i));
        }
        return answer(codes, c, aliases, Map.of());
    }

    public static Function<LlmRequest, LlmResponse> noisy(EvalCase c, IngredientAliases aliases, FakeLlmClient fake,
                                                         ObjectMapper objectMapper, long seed) {
        return request -> {
            Random random = new Random(seed * 31 + (LlmRequest.TAG_RECOMMEND_REPAIR.equals(request.callTag()) ? 7 : 0));
            boolean toolMode = !request.toolsOrEmpty().isEmpty();
            boolean repair = LlmRequest.TAG_RECOMMEND_REPAIR.equals(request.callTag());
            // 형식 오류: V0는 JSON이 아닌 문장, tool use는 모르는 성분 코드 (재요청에는 정상 응답)
            if (!repair && random.nextDouble() < 0.15) {
                if (!toolMode) {
                    return fake.textResponse(request, "추천 성분을 정리해 드릴게요. 비타민 D와 마그네슘을 고려해 보세요.");
                }
                ObjectNode bad = objectMapper.createObjectNode();
                ObjectNode item = bad.putArray("recommendations").addObject();
                item.put("ingredient", "VITAMIN_E");
                item.put("reason", "가짜 오류");
                item.put("priority", 1);
                return fake.toolResponse(request, request.toolsOrEmpty().get(0).name(), bad);
            }
            Set<String> codes = new LinkedHashSet<>(oracle(c, aliases).items().stream()
                    .map(FakeAnswer.Item::code).toList());
            List<String> mustInclude = c.expected().mustInclude();
            if (!mustInclude.isEmpty() && random.nextDouble() < 0.3) {
                codes.remove(mustInclude.get(random.nextInt(mustInclude.size())));
            }
            List<String> mustExclude = c.expected().mustExclude();
            if (!mustExclude.isEmpty()) {
                String banned = mustExclude.get(random.nextInt(mustExclude.size()));
                double p = promptForbids(request, banned) ? 0.05 : 0.4;
                if (random.nextDouble() < p) {
                    codes.add(banned);
                }
            }
            Map<String, BigDecimal> scale = codes.stream().filter(x -> random.nextDouble() < 0.4)
                    .collect(Collectors.toMap(x -> x, x -> BigDecimal.valueOf(0.7 + random.nextDouble() * 0.6)));
            FakeAnswer answer = answer(codes, c, aliases, scale);
            if (!toolMode && random.nextDouble() < 0.1) {
                // 코드펜스로 감싼 JSON (V0 파서는 첫 '{'~마지막 '}'를 읽으므로 성공해야 한다)
                return fake.textResponse(request, "```json\n" + v0Json(answer, objectMapper) + "\n```");
            }
            return fake.render(request, answer);
        };
    }

    private static boolean promptForbids(LlmRequest request, String code) {
        String all = (request.system() == null ? "" : request.system()) + "\n"
                + request.messages().stream().map(LlmMessage::text).collect(Collectors.joining("\n"));
        int idx = all.indexOf("추천하지 마라");
        return idx >= 0 && all.substring(idx).contains(code);
    }

    private static String v0Json(FakeAnswer answer, ObjectMapper objectMapper) {
        ObjectNode root = objectMapper.createObjectNode();
        var arr = root.putArray("recommendations");
        for (FakeAnswer.Item item : answer.items()) {
            ObjectNode n = arr.addObject();
            n.put("name", item.displayName());
            n.put("reason", item.reason());
            if (item.amount() != null) {
                n.put("amount", item.amount());
                n.put("unit", item.unit());
            }
        }
        root.put("summary", answer.summary());
        return root.toString();
    }

    private static FakeAnswer answer(Set<String> codes, EvalCase c, IngredientAliases aliases,
                                     Map<String, BigDecimal> scale) {
        Map<String, Dose> doses = c.expected().doses().stream()
                .collect(Collectors.toMap(Dose::ingredient, d -> d, (a, b) -> a));
        List<FakeAnswer.Item> items = new ArrayList<>();
        for (String code : codes) {
            Dose d = doses.get(code);
            BigDecimal amount = d == null ? null : d.amount();
            if (amount != null && scale.containsKey(code)) {
                amount = amount.multiply(scale.get(code)).setScale(0, RoundingMode.HALF_UP);
            }
            items.add(new FakeAnswer.Item(code, aliases.displayName(code), "가짜 응답: " + c.caseId(), amount,
                    d == null ? null : d.unit()));
        }
        return new FakeAnswer(items, "가짜 응답 " + c.caseId());
    }
}
