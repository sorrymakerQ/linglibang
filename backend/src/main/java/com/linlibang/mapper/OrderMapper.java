package com.linlibang.mapper;

import com.linlibang.entity.Order;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 订单 Mapper 接口（MyBatis 注解方式）
 */
@Mapper
public interface OrderMapper {

    /**
     * 插入订单，自动回填ID
     */
    @Insert("INSERT INTO tb_order " +
            "(help_id, publisher_id, helper_id, status, cancel_reason, " +
            "accept_time, finish_time, publisher_score, helper_score, publisher_comment, helper_comment, " +
            "create_time, update_time) " +
            "VALUES (#{helpId}, #{publisherId}, #{helperId}, #{status}, #{cancelReason}, " +
            "NOW(), #{finishTime}, #{publisherScore}, #{helperScore}, #{publisherComment}, #{helperComment}, " +
            "NOW(), NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Order order);

    /**
     * 根据ID查询订单
     */
    @Select("SELECT * FROM tb_order WHERE id = #{id}")
    Order selectById(@Param("id") Long id);

    /**
     * 根据ID动态更新订单（只更新非空字段）
     */
    @Update("<script>" +
            "UPDATE tb_order SET update_time = NOW()" +
            "<if test='helpId != null'>, help_id = #{helpId}</if>" +
            "<if test='publisherId != null'>, publisher_id = #{publisherId}</if>" +
            "<if test='helperId != null'>, helper_id = #{helperId}</if>" +
            "<if test='status != null'>, status = #{status}</if>" +
            "<if test='cancelReason != null'>, cancel_reason = #{cancelReason}</if>" +
            "<if test='acceptTime != null'>, accept_time = #{acceptTime}</if>" +
            "<if test='finishTime != null'>, finish_time = #{finishTime}</if>" +
            "<if test='publisherScore != null'>, publisher_score = #{publisherScore}</if>" +
            "<if test='helperScore != null'>, helper_score = #{helperScore}</if>" +
            "<if test='publisherComment != null'>, publisher_comment = #{publisherComment}</if>" +
            "<if test='helperComment != null'>, helper_comment = #{helperComment}</if>" +
            " WHERE id = #{id}" +
            "</script>")
    int updateById(Order order);

    /**
     * 按状态统计订单数
     */
    @Select("SELECT COUNT(*) FROM tb_order WHERE status = #{status}")
    Long selectCountByStatus(@Param("status") Integer status);

    /**
     * 按用户ID和角色分页查询订单
     */
    @Select("<script>" +
            "SELECT * FROM tb_order WHERE " +
            "<choose>" +
            "<when test='role == \"publisher\"'>publisher_id = #{userId}</when>" +
            "<when test='role == \"helper\"'>helper_id = #{userId}</when>" +
            "<otherwise>(publisher_id = #{userId} OR helper_id = #{userId})</otherwise>" +
            "</choose>" +
            " ORDER BY create_time DESC LIMIT #{offset}, #{size}" +
            "</script>")
    List<Order> selectByUserIdAndRole(@Param("userId") Long userId,
                                      @Param("role") String role,
                                      @Param("offset") int offset,
                                      @Param("size") int size);

    /**
     * 按用户ID和角色统计订单总数
     */
    @Select("<script>" +
            "SELECT COUNT(*) FROM tb_order WHERE " +
            "<choose>" +
            "<when test='role == \"publisher\"'>publisher_id = #{userId}</when>" +
            "<when test='role == \"helper\"'>helper_id = #{userId}</when>" +
            "<otherwise>(publisher_id = #{userId} OR helper_id = #{userId})</otherwise>" +
            "</choose>" +
            "</script>")
    Long selectCountByUserIdAndRole(@Param("userId") Long userId, @Param("role") String role);

    /**
     * 查询某求助的当前接单人的活跃订单（status=1已接单 / 2进行中）
     */
    @Select("SELECT * FROM tb_order WHERE help_id = #{helpId} AND status IN (1, 2) ORDER BY create_time DESC LIMIT 1")
    Order selectActiveByHelpId(@Param("helpId") Long helpId);
}
