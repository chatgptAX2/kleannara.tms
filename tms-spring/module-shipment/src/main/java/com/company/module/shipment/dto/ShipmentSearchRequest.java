package com.company.module.shipment.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 출고진행현황 검색 요청 DTO
 * JS body 키명과 Java 필드명 매핑:
 *   rqshpd_from → dateFrom
 *   rqshpd_to   → dateTo
 *   lota02      → lota02List
 */
@Getter
@Setter
public class ShipmentSearchRequest {

    private String wareky;                  // 창고코드

    @JsonAlias("rqshpd_from")
    @Pattern(regexp = "^\\d{8}$|^$", message = "날짜 형식은 yyyyMMdd 이어야 합니다.")
    private String dateFrom;                // 검색 시작일 (yyyyMMdd) — JS: rqshpd_from

    @JsonAlias("rqshpd_to")
    @Pattern(regexp = "^\\d{8}$|^$", message = "날짜 형식은 yyyyMMdd 이어야 합니다.")
    private String dateTo;                  // 검색 종료일 (yyyyMMdd) — JS: rqshpd_to

    private String statdo;                  // 출고상태코드

    private String skug05;                  // 품목그룹05

    @JsonAlias("lota02")
    private List<String> lota02List;        // 플랜트(LOTA02) 다중 선택 — JS: lota02

    @JsonAlias("skukey")
    private String keyword;                 // 품목코드/품목명 검색어 — JS: skukey

    private int page = 1;                   // 페이지 번호 (1-based, JS에서 1 전송)

    private int size = 500;                 // 페이지 크기

    /** yyyyMMdd 정규화 (하이픈 제거) */
    public String normalizedDateFrom() {
        return dateFrom == null ? "" : dateFrom.replace("-", "").strip();
    }

    public String normalizedDateTo() {
        return dateTo == null ? "" : dateTo.replace("-", "").strip();
    }
}
