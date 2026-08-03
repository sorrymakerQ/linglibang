package com.linlibang.mapper;

import com.linlibang.entity.HelpRequest;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 求助 Mapper 接口（MyBatis 注解方式）
 */
@Mapper
public interface HelpRequestMapper {

    /**
     * 插入求助，自动回填ID
     */
    @Insert("INSERT INTO tb_help_request " +
            "(user_id, category_id, title, description, images, reward, address, lng, lat, " +
            "status, urgent, view_count, create_time, update_time, is_deleted) " +
            "VALUES (#{userId}, #{categoryId}, #{title}, #{description}, #{images}, #{reward}, " +
            "#{address}, #{lng}, #{lat}, #{status}, #{urgent}, #{viewCount}, NOW(), NOW(), 0)")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(HelpRequest help);

    /**
     * 根据ID查询求助
     */
    @Select("SELECT * FROM tb_help_request WHERE id = #{id} AND is_deleted = 0")
    HelpRequest selectById(@Param("id") Long id);

    /**
     * 批量查询求助（用于避免 N+1 查询）
     */
    @Select("<script>" +
            "SELECT * FROM tb_help_request WHERE id IN " +
            "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach>" +
            " AND is_deleted = 0" +
            "</script>")
    List<HelpRequest> selectByIds(@Param("ids") List<Long> ids);

    /**
     * 根据ID动态更新求助（只更新非空字段）
     */
    @Update("<script>" +
            "UPDATE tb_help_request SET update_time = NOW()" +
            "<if test='userId != null'>, user_id = #{userId}</if>" +
            "<if test='categoryId != null'>, category_id = #{categoryId}</if>" +
            "<if test='title != null'>, title = #{title}</if>" +
            "<if test='description != null'>, description = #{description}</if>" +
            "<if test='images != null'>, images = #{images}</if>" +
            "<if test='reward != null'>, reward = #{reward}</if>" +
            "<if test='address != null'>, address = #{address}</if>" +
            "<if test='lng != null'>, lng = #{lng}</if>" +
            "<if test='lat != null'>, lat = #{lat}</if>" +
            "<if test='status != null'>, status = #{status}</if>" +
            "<if test='urgent != null'>, urgent = #{urgent}</if>" +
            "<if test='viewCount != null'>, view_count = #{viewCount}</if>" +
            "<if test='isDeleted != null'>, is_deleted = #{isDeleted}</if>" +
            " WHERE id = #{id}" +
            "</script>")
    int updateById(HelpRequest help);

    /**
     * 根据ID列表和状态查询求助（附近搜索用）
     */
    @Select("<script>" +
            "SELECT * FROM tb_help_request WHERE is_deleted = 0 AND status = #{status} " +
            "AND id IN <foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach> " +
            "ORDER BY urgent DESC, create_time DESC" +
            "</script>")
    List<HelpRequest> selectByIdsAndStatus(@Param("ids") List<Long> ids, @Param("status") Integer status);

    /**
     * 分页查询用户的求助列表
     */
    @Select("SELECT * FROM tb_help_request WHERE user_id = #{userId} AND is_deleted = 0 " +
            "ORDER BY create_time DESC LIMIT #{offset}, #{size}")
    List<HelpRequest> selectByUserIdPaged(@Param("userId") Long userId,
                                          @Param("offset") int offset,
                                          @Param("size") int size);

    /**
     * 用户求助总数
     */
    @Select("SELECT COUNT(*) FROM tb_help_request WHERE user_id = #{userId} AND is_deleted = 0")
    Long selectCountByUserId(@Param("userId") Long userId);

    /**
     * 搜索求助（关键词 + 分类筛选）
     */
    @Select("<script>" +
            "SELECT * FROM tb_help_request WHERE status = 1 AND is_deleted = 0" +
            "<if test='categoryId != null'> AND category_id = #{categoryId}</if>" +
            "<if test='keyword != null and keyword != \"\"'> " +
            "AND (title LIKE CONCAT('%',#{keyword},'%') OR description LIKE CONCAT('%',#{keyword},'%'))</if>" +
            " ORDER BY urgent DESC, create_time DESC LIMIT #{offset}, #{size}" +
            "</script>")
    List<HelpRequest> search(@Param("keyword") String keyword,
                             @Param("categoryId") Long categoryId,
                             @Param("offset") int offset,
                             @Param("size") int size);

    /**
     * 搜索求助总数
     */
    @Select("<script>" +
            "SELECT COUNT(*) FROM tb_help_request WHERE status = 1 AND is_deleted = 0" +
            "<if test='categoryId != null'> AND category_id = #{categoryId}</if>" +
            "<if test='keyword != null and keyword != \"\"'> " +
            "AND (title LIKE CONCAT('%',#{keyword},'%') OR description LIKE CONCAT('%',#{keyword},'%'))</if>" +
            "</script>")
    Long searchCount(@Param("keyword") String keyword, @Param("categoryId") Long categoryId);

    /**
     * 查询求助总数
     */
    @Select("<script>" +
            "SELECT COUNT(*) FROM tb_help_request WHERE status = 1 AND is_deleted = 0" +
            "<if test='categoryId != null'> AND category_id = #{categoryId}</if>" +
            "</script>")
    Long selectCount(@Param("categoryId") Long categoryId);

    @Select("SELECT COUNT(*) FROM tb_help_request WHERE status = #{status} AND is_deleted = 0")
    Long selectCountByStatus(@Param("status") Integer status);

    @Select("<script>" +
            "SELECT * FROM tb_help_request WHERE status = 1 AND is_deleted = 0" +
            "<if test='categoryId != null'> AND category_id = #{categoryId}</if>" +
            " ORDER BY create_time DESC LIMIT #{offset}, #{size}" +
            "</script>")
    List<HelpRequest> selectPage(@Param("offset") int offset, @Param("size") int size,
                                 @Param("categoryId") Long categoryId);

    /**
     * 管理员分页查询求助（不过滤状态）
     */
    @Select("<script>" +
            "SELECT * FROM tb_help_request WHERE is_deleted = 0" +
            "<if test='status != null'> AND status = #{status}</if>" +
            " ORDER BY create_time DESC LIMIT #{offset}, #{size}" +
            "</script>")
    List<HelpRequest> selectPageAll(@Param("offset") int offset, @Param("size") int size,
                                    @Param("status") Integer status);

    /**
     * 管理员查询求助总数（不过滤状态）
     */
    @Select("<script>" +
            "SELECT COUNT(*) FROM tb_help_request WHERE is_deleted = 0" +
            "<if test='status != null'> AND status = #{status}</if>" +
            "</script>")
    Long selectCountAll(@Param("status") Integer status);
}
