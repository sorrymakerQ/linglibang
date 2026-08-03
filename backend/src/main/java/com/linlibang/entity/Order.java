package com.linlibang.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 订单实体
 */
@Data
public class Order {

    /** 主键ID */
    private Long id;

    /** 关联的求助ID */
    private Long helpId;

    /** 发布者用户ID */
    private Long publisherId;

    /** 接单者用户ID */
    private Long helperId;

    /** 状态：1已接单 2进行中 3已完成 4已取消 5已评价 */
    private Integer status;

    /** 取消原因 */
    private String cancelReason;

    /** 接单时间 */
    private LocalDateTime acceptTime;

    /** 完成时间 */
    private LocalDateTime finishTime;

    /** 发布者对帮助者的评分 */
    private Integer publisherScore;

    /** 帮助者对发布者的评分 */
    private Integer helperScore;

    /** 发布者对帮助者的评价 */
    private String publisherComment;

    /** 帮助者对发布者的评价 */
    private String helperComment;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
