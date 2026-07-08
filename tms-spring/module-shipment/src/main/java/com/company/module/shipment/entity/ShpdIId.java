package com.company.module.shipment.entity;

import java.io.Serializable;
import lombok.*;

/**
 * SHPDI 복합키 (SHPOKY + SHPOIT)
 */
@Getter
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
public class ShpdIId implements Serializable {
    private String shpoky;
    private String shpoit;
}
