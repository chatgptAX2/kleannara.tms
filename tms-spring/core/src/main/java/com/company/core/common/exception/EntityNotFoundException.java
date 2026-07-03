package com.company.core.common.exception;

import lombok.Getter;

/**
 * 엔티티 조회 실패 예외 (404)
 */
@Getter
public class EntityNotFoundException extends BusinessException {
    public EntityNotFoundException(String entityName, Object id) {
        super(ErrorCode.ENTITY_NOT_FOUND,
              String.format("%s (id=%s) not found", entityName, id));
    }
}
