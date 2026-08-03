package com.linlibang.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户实体
 */
@Data
public class User {

    /** 主键ID */
    private Long id;

    /** 手机号 */
    private String phone;

    /** 密码（BCrypt加密） */
    private String password;

    /** 昵称 */
    private String nickname;

    /** 头像URL */
    private String avatar;

    /** 性别：0未知 1男 2女 */
    private Integer gender;

    /** 所在小区/社区 */
    private String community;

    /** 经度 */
    private Double lng;

    /** 纬度 */
    private Double lat;

    /** 信用分 */
    private Integer credit;

    /** 帮助次数 */
    private Integer helpCount;

    /** 个人简介 */
    private String intro;

    /** 角色：1普通用户 2管理员 */
    private Integer role;

    /** 状态：1正常 0禁用 */
    private Integer status;

    /** 权限码列表，逗号分隔（如 help:publish,order:accept,message:send） */
    private String permissions;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /** 逻辑删除：0未删除 1已删除 */
    private Integer isDeleted;
}
