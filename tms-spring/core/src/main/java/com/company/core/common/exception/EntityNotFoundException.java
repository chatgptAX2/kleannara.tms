package com.company.core.common.exception;

import lombok.Getter;

/**
 * 엔티티 조회 실패 예외 (404)
 */
@Getter
public class EntityNotFoundException extends BusinessException {

    /** ErrorCode만으로 예외 생성 (모듈에서 직접 코드를 지정할 때) */
    public EntityNotFoundException(ErrorCode errorCode) {
        super(errorCode);
    }

    /** entityName + id 조합으로 예외 생성 */
    public EntityNotFoundException(String entityName, Object id) {
        super(ErrorCode.ENTITY_NOT_FOUND,
              String.format("%s (id=%s) not found", entityName, id));
    }
}
