package com.ucamp.app.domain.product.service;

import com.ucamp.app.domain.product.client.MfdsClient;
import com.ucamp.app.domain.product.dto.CatalogImportResponse;
import com.ucamp.app.domain.product.dto.MfdsProduct;
import com.ucamp.app.domain.product.entity.CatalogProduct;
import com.ucamp.app.domain.product.repository.CatalogProductRepository;
import com.ucamp.app.global.exception.BusinessException;
import com.ucamp.app.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// 식약처 건강기능식품 한 페이지를 catalog_products에 저장
// 흐름: 식약처 1페이지 받기 → 원료·수출용 빼기 → 품목번호로 이미 있으면 수정, 없으면 추가
@Service
@RequiredArgsConstructor
public class CatalogProductImportService {

    // 한 번에 받을 수 있는 최대 개수 (식약처 호출 1번)
    private static final int MAX_ROWS = 100;

    // 제품명 괄호 안에 이 나라 이름이 있으면 해외 판매용 (예: "VITAMIN C(싱가포르,캄보디아,몽골)")
    private static final List<String> COUNTRIES = List.of(
            "일본", "중국", "대만", "홍콩", "마카오", "베트남", "태국", "인도네시아", "말레이시아",
            "싱가포르", "필리핀", "캄보디아", "라오스", "미얀마", "몽골", "인도", "네팔", "스리랑카",
            "우즈베키스탄", "카자흐스탄", "키르기스스탄", "러시아", "터키", "튀르키예", "UAE", "두바이",
            "미국", "캐나다", "멕시코", "브라질", "페루", "칠레", "콜롬비아",
            "호주", "뉴질랜드", "영국", "독일", "프랑스", "스페인", "이탈리아", "네덜란드", "폴란드", "유럽");

    private final MfdsClient mfdsClient;
    private final CatalogProductRepository catalogProductRepository;

    @Transactional
    public CatalogImportResponse importPage(int pageNo, int numOfRows) {
        if (pageNo < 1 || numOfRows < 1 || numOfRows > MAX_ROWS) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        // 1. 식약처에서 한 페이지 받기 → 저장할 제품만 고르기
        List<MfdsProduct> fetched = mfdsClient.fetchPage(pageNo, numOfRows);
        List<MfdsProduct> targets = new ArrayList<>();
        List<String> itemNos = new ArrayList<>();
        for (MfdsProduct product : fetched) {
            if (isSkipped(product)) {
                continue;
            }
            // 한 페이지에 같은 품목번호가 두 번 오면 처음 것만 저장 (중복 행 방지)
            if (itemNos.contains(product.itemNo())) {
                continue;
            }
            targets.add(product);
            itemNos.add(product.itemNo());
        }

        // 2. 이미 저장된 제품을 한 번에 조회 (품목번호 → 제품)
        Map<String, CatalogProduct> saved = new HashMap<>();
        if (!itemNos.isEmpty()) {
            for (CatalogProduct product : catalogProductRepository.findByMfdsItemNoIn(itemNos)) {
                saved.put(product.getMfdsItemNo(), product);
            }
        }

        // 3. 있으면 식약처 값으로 수정(변경 감지), 없으면 새로 저장
        int created = 0;
        int updated = 0;
        for (MfdsProduct product : targets) {
            String category = toCategory(product);
            String summary = toIngredientSummary(product.baseStandard());
            String mainFunction = firstFunction(product.mainFunction());
            CatalogProduct existing = saved.get(product.itemNo());
            if (existing != null) {
                existing.updateFromMfds(product.productName(), product.manufacturer(), mainFunction, product.intakeMethod(), product.intakeCaution(), category, summary);
                updated++;
            } else {
                catalogProductRepository.save(CatalogProduct.builder()
                        .mfdsItemNo(product.itemNo())
                        .productName(product.productName())
                        .manufacturer(product.manufacturer())
                        .mainFunction(mainFunction)
                        .intakeMethod(product.intakeMethod())
                        .intakeCaution(product.intakeCaution())
                        .category(category)
                        .ingredientSummary(summary)
                        .build());
                created++;
            }
        }
        int skipped = fetched.size() - targets.size();
        return new CatalogImportResponse(fetched.size(), created, updated, skipped);
    }
    // 저장하지 않는 제품: 번호나 이름이 없음, 원료용(소비자 제품 아님), 수출용
    private boolean isSkipped(MfdsProduct product) {
        if (product.itemNo() == null ||
        product.productName() == null) {
            return true;
        }
        if(product.intakeMethod() != null && product.intakeMethod().contains("원료로 사용")){
            return true;
        }
        // 제품명에 "수출"이 들어가면 국내 판매 제품이 아니라고 보고 건너뜀 (수출용, 전량수출용 등)
        if (product.productName().contains("수출")) {
            return true;
        }

        // 제품명 괄호에 나라 이름이 있으면 해외 판매용으로 보고 건너뜀
        if (hasCountryInBracket(product.productName())) {
            return true;
        }
        // 제품명에 한글이 하나도 없으면 해외 판매용으로 보고 건너뜀 (예: ATOMY VITAMIN C(브라질))
        return !hasKorean(product.productName());
    }



    private boolean hasKorean(String text) {
        for(char c : text.toCharArray()) {
            if(c >= '가' && c <= '힣') {
                return true;
            }
        }
        return false;
    }


    // 주된 기능성에서 첫 번째 기능만 남김 (홈 카드에 길게 나오지 않게)
    // 식약처 번호 표기가 제품마다 달라서 3가지 모두 확인: (1) / ① / ⑴
    // 예: "⑴ 면역력 증진에 도움을 줄 수 있음 ⑵ 피로개선..." → "면역력 증진에 도움을 줄 수 있음"
    private String firstFunction(String mainFunction) {
        if (mainFunction == null) {
            return null;
        }
        String text = mainFunction;
        String[] firstMarks = {"(1)", "①", "⑴"};
        String[] secondMarks = {"(2)", "②", "⑵"};

        // 앞의 1번 표시 떼기
        for (String mark : firstMarks) {
            if (text.startsWith(mark)) {
                text = text.substring(mark.length());
            }
        }
        // 2번 표시가 나오기 전까지만 남기기
        for (String mark : secondMarks) {
            int next = text.indexOf(mark);
            if (next > 0) {
                text = text.substring(0, next);
            }
        }

        text = text.trim();
        // 번호 없이 긴 글은 앞 20글자만 남기고 "…" 붙이기
        if (text.length() > 20) {
            text = text.substring(0, 20) + "…";
        }
        return text;
    }

    // 제품명 마지막 괄호 안에 나라 이름이 하나라도 있는지 확인
    // 예: "ATOMY VITAMIN C(영국,독일)" → 괄호 안 "영국,독일" → "영국"이 있으니 true
    private boolean hasCountryInBracket(String name) {
        int start = name.lastIndexOf("(");
        int end = name.lastIndexOf(")");
        if (start < 0 || end < start) {
            return false;
        }
        String inside = name.substring(start + 1, end);
        for (String country : COUNTRIES) {
            if (inside.contains(country)) {
                return true;
            }
        }
        return false;
    }

    // 카테고리 자동 분류: 제품명으로 먼저 판단하고, 없으면 주된 기능성으로 판단
    // 식약처 데이터에 카테고리 칸이 없어서 화면 표시용으로만 정함
    private String toCategory(MfdsProduct product) {
        String category = findCategory(product.productName());
        if (category == null && product.mainFunction() != null) {
            category = findCategory(product.mainFunction());
        }
        if (category == null) {
            return "기타";
        }
        return category;
    }

    // 글에 들어 있는 단어로 카테고리 찾기 (위에서부터 먼저 맞는 것), 없으면 null
    // 미네랄을 비타민보다 먼저 확인 (예: "7030칼슘마그비타"는 미네랄)
    private String findCategory(String text) {
        if (text.contains("홍삼") || text.contains("인삼") || text.contains("진세노사이드")) {
            return "홍삼";
        }
        if (text.contains("유산균") || text.contains("프로바이오틱스")) {
            return "유산균";
        }
        if (text.contains("오메가") || text.contains("EPA") || text.contains("DHA")) {
            return "오메가3";
        }
        if (text.contains("루테인")) {
            return "눈 건강";
        }
        if (text.contains("칼슘") || text.contains("마그네슘") || text.contains("아연") || text.contains("철")) {
            return "미네랄";
        }
        if (text.contains("비타민") || text.contains("VITAMIN") || text.contains("Vitamin")) {
            return "비타민";
        }
        return null;
    }

    private String toIngredientSummary(String baseStandard){
        if(baseStandard == null) {
            return null;
        }
        String summary = "";
        int count = 0;
        for(String line : baseStandard.split("\n")) {
            int colon = line.indexOf(":");
            int mark = line.indexOf("표시량");
            // "성분 : … 표시량 …" 모양이 아닌 줄은 건너뜀 (성상, 납, 대장균군 등)
            if (colon < 0 || mark < colon) {
                continue;
            }
            String item = null;  // 이 줄에서 찾은 "성분 양" (예: "칼슘 229.8mg")

            // "표시량" 뒤의 공백과 "("를 건너뛰고 숫자가 나오면 함량이 "표시량" 뒤에 있는 형식
            int amountStart = mark + 3;
            while (amountStart < line.length() && (line.charAt(amountStart) == ' ' || line.charAt(amountStart) == '(')) {
                amountStart++;
            }
            if (amountStart < line.length() && Character.isDigit(line.charAt(amountStart))) {
                // 형식 1: "(2) 비타민 C : 표시량(500 mg/2 g)의 80~150%", "2)칼슘 : 표시량 250㎎/920㎎ 의 80~150%"
                String name = removeNumberMark(line.substring(0, colon));
                int amountEnd = line.indexOf("/", amountStart);
                int close = line.indexOf(")", amountStart);
                if (amountEnd < 0 || (close >= 0 && close < amountEnd)) {
                    amountEnd = close;
                }
                if (!name.isEmpty() && amountEnd > amountStart) {
                    item = name + " " + line.substring(amountStart, amountEnd).replace(" ", "");
                }
            } else {
                // 형식 2: "2)칼슘 229.8mg/920mg : 표시량의 80~150%" (함량이 ":" 앞에 있음)
                String before = line.substring(0, colon);
                int slash = before.indexOf("/");
                if (slash > 0) {
                    item = removeNumberMark(before.substring(0, slash));
                }
            }
            if (item == null || item.isEmpty()) {
                continue;
            }
            if (count > 0) {
                summary = summary + ", ";
            }
            summary = summary + item;
            count++;
            if (count == 2) {
                break;
            }
        }

        if (summary.isEmpty()) {
            return null;
        }
        if(summary.length() > 255) {
            summary = summary.substring(0, 255);
        }
        return summary;
    }

    // 앞쪽 번호 표시 "(2)", "②", "2)" 등을 떼고 성분 이름부터 남김
    private String removeNumberMark(String text) {
        int start = 0;
        while (start < text.length() && !Character.isLetter(text.charAt(start))) {
            start++;
        }
        return text.substring(start).trim();
    }
}