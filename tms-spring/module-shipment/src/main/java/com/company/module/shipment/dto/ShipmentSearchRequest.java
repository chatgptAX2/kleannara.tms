package com.company.module.shipment.dto;

import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 출고진행현황 검색 요청 DTO
 */
@Getter
@Setter
public class ShipmentSearchRequest {

    private String wareky;                  // 창고코드

    @Pattern(regexp = "^\\d{8}$|^$", message = "날짜 형식은 yyyyMMdd 이어야 합니다.")
    private String dateFrom;                // 검색 시작일 (yyyyMMdd)

    @Pattern(regexp = "^\\d{8}$|^$", message = "날짜 형식은 yyyyMMdd 이어야 합니다.")
    private String dateTo;                  // 검색 종료일 (yyyyMMdd)

    private String statdo;                  // 출고상태코드

    private String skug05;                  // 품목그룹05

    private List<String> lota02List;        // 플랜트(LOTA02) 다중 선택

    private String keyword;                 // 품목코드/품목명 검색어

    private int page = 0;                   // 페이지 번호 (0-based)

    private int size = 100;                 // 페이지 크기

    /** yyyyMMdd 정규화 (하이픈 제거) */
    public String normalizedDateFrom() {
        return dateFrom == null ? "" : dateFrom.replace("-", "").strip();
    }

    public String normalizedDateTo() {
        return dateTo == null ? "" : dateTo.replace("-", "").strip();
    }
}
