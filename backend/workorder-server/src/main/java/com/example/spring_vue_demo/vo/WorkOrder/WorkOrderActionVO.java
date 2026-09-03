package com.example.spring_vue_demo.vo.WorkOrder;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** An action the authenticated user may currently perform on a work order. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WorkOrderActionVO {
    private String type;
    private String label;
    private boolean requireAssignedUser;
    private boolean requireRemark;
    private boolean dangerous;
}
