package com.company.module.delivery.entity.wms;

import lombok.*;

import java.io.Serializable;

/**
 * BzptnDetail 복합 기본키 클래스 (@IdClass 용).
 *
 * 실제 운영 테이블 KNRAWMS.BZPTN_DETAIL 의 PK 구성:
 *   PTNRKY + PTNRTY + OWNRKY + WAREKY
 *
 * ※ 기존 엔티티는 존재하지 않는 DETAIL_ID(IDENTITY)를 PK 로 매핑하여
 *   상세조회 SELECT 시 ORA-00904 ("DETAIL_ID": 부적합한 식별자)가 발생했다.
 *   → 실제 테이블 구조에 맞춰 복합 PK 로 전환한다.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class BzptnDetailId implements Serializable {
    private String ptnrky;   // 납품처코드
    private String ptnrty;   // 파트너 유형 (CT)
    private String ownrky;   // 사업주 (KN)
    private String wareky;   // 출하 창고코드
}
