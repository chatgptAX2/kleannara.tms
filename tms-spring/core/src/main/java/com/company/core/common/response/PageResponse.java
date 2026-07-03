package com.company.core.common.response;

import org.springframework.data.domain.Page;
import java.util.List;

/**
 * 페이징 응답 래퍼
 */
public record PageResponse<T>(
        List<T>  content,
        int      page,
        int      size,
        long     totalElements,
        int      totalPages,
        boolean  last
) {
    public static <T> PageResponse<T> of(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isLast()
        );
    }
}
