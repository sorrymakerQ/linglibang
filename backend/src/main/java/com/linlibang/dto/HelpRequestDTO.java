package com.linlibang.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;

/**
 * 发布求助表单 DTO
 */
@Data
public class HelpRequestDTO {

    @NotNull(message = "求助分类不能为空")
    private Long categoryId;

    @NotBlank(message = "求助标题不能为空")
    private String title;

    @NotBlank(message = "详细描述不能为空")
    private String description;

    /** 图片URL数组 */
    private List<String> images;

    /** 酬劳金额，0表示免费求助 */
    private BigDecimal reward;

    @NotBlank(message = "地址不能为空")
    private String address;

    @NotNull(message = "经度不能为空")
    private Double lng;

    @NotNull(message = "纬度不能为空")
    private Double lat;

    /** 是否紧急：0-普通，1-紧急 */
    private Integer urgent;
}
