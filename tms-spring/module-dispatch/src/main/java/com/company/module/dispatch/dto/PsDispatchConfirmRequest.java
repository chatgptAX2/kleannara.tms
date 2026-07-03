package com.company.module.dispatch.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/** 배차 확정 요청 DTO – Flask: POST /api/ps-dispatch/confirm */
@Getter
@Setter
public class PsDispatchConfirmRequest {

    @NotEmpty(message = "dispatch_nos는 필수입니다")
    private List<String> dispatchNos;
}
