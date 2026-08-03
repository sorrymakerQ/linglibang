package com.linlibang.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 分类实体
 */
@Data
public class Category {

    /** 主键ID */
    private Long id;

    /** 分类名称 */
    private String name;

    /** 图标 */
    private String icon;

    /** 排序 */
    private Integer sort;

    /** 创建时间 */
    private LocalDateTime createTime;
}
