package com.linlibang.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 通知实体
 */
@Data
public class Notification {

    /** 主键ID */
    private Long id;

    /** 接收通知的用户ID */
    private Long userId;

    /** 通知标题 */
    private String title;

    /** 通知内容 */
    private String content;

    /** 通知类型：1系统通知 2订单通知 3评价通知 */
    private Integer type;

    /** 是否已读：0未读 1已读 */
    private Integer isRead;

    /** 关联的业务ID */
    private Long relatedId;

    /** 创建时间 */
    private LocalDateTime createTime;
}
