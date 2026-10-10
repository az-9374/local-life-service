package com.chen.locallife.dto;

import lombok.Data;

import java.util.List;

@Data
public class ScrollResult {//啥
    private List<?> list;
    private Long minTime;
    private Integer offset;
}
