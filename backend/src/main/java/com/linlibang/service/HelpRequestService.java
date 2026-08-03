package com.linlibang.service;

import com.linlibang.dto.HelpRequestDTO;
import com.linlibang.dto.Result;

/**
 * 求助服务接口
 */
public interface HelpRequestService {

    /**
     * 发布求助
     *
     * @param dto     求助表单
     * @param userId  发布者ID
     * @return 发布结果
     */
    Result publishHelp(HelpRequestDTO dto, Long userId);

    /**
     * 查询附近的求助
     *
     * @param lng     中心经度
     * @param lat     中心纬度
     * @param radius     搜索半径（公里），默认5
     * @param page       页码
     * @param size       每页条数
     * @param categoryId 分类ID（可选）
     * @return 附近求助列表
     */
    Result getNearbyHelp(Double lng, Double lat, Integer radius, Integer page, Integer size, Long categoryId, String keyword);

    /**
     * 查询求助详情
     *
     * @param helpId 求助ID
     * @return 求助详情
     */
    Result getHelpById(Long helpId);

    /**
     * 取消求助（仅发布者可操作）
     *
     * @param helpId 求助ID
     * @param userId 操作者ID
     * @return 取消结果
     */
    Result cancelHelp(Long helpId, Long userId);

    /**
     * 查询我的求助列表
     *
     * @param userId 用户ID
     * @param page   页码
     * @param size   每页条数
     * @return 求助列表
     */
    Result getMyHelp(Long userId, Integer page, Integer size);

    /**
     * 搜索求助
     *
     * @param keyword    关键词
     * @param categoryId 分类ID（可选）
     * @param page       页码
     * @param size       每页条数
     * @return 搜索结果
     */
    Result searchHelp(String keyword, Long categoryId, Integer page, Integer size);
}
