package com.linlibang.dto;

import lombok.Data;

/**
 * 用户信息 DTO（脱敏，不含密码）
 */
@Data
public class UserDTO {

    private Long id;

    private String phone;

    private String nickname;

    private String avatar;

    /** 性别：0-未知，1-男，2-女 */
    private Integer gender;

    /** 所在小区 */
    private String community;

    /** 经度 */
    private Double lng;

    /** 纬度 */
    private Double lat;

    /** 信用分 */
    private Integer credit;

    /** 累计帮助次数 */
    private Integer helpCount;

    /** 个人简介 */
    private String intro;

    /** 角色：1-普通用户，2-管理员 */
    private Integer role;

    /** 权限码列表，逗号分隔 */
    private String permissions;
}
