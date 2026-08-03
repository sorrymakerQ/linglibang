package com.linlibang.mapper;

import com.linlibang.entity.ChatMessage;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface ChatMessageMapper {

    @Insert("<script>" +
            "INSERT INTO tb_chat_message (help_id, order_id, sender_id, receiver_id, content, is_read, create_time) " +
            "VALUES (#{helpId}, #{orderId}, #{senderId}, #{receiverId}, #{content}, 0, NOW())" +
            "</script>")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(ChatMessage msg);

    @Select("SELECT * FROM tb_chat_message WHERE order_id = #{orderId} ORDER BY create_time ASC")
    List<ChatMessage> selectByOrderId(@Param("orderId") Long orderId);

    @Select("SELECT * FROM tb_chat_message WHERE help_id = #{helpId} AND order_id IS NULL ORDER BY create_time ASC")
    List<ChatMessage> selectByHelpId(@Param("helpId") Long helpId);

    @Select("SELECT * FROM tb_chat_message WHERE help_id = #{helpId} AND order_id IS NULL AND (sender_id = #{userId} OR receiver_id = #{userId}) ORDER BY create_time ASC")
    List<ChatMessage> selectByHelpIdAndUser(@Param("helpId") Long helpId, @Param("userId") Long userId);

    @Update("<script>" +
            "UPDATE tb_chat_message SET is_read = 1 WHERE receiver_id = #{userId} AND is_read = 0" +
            "<if test='orderId != null'> AND order_id = #{orderId}</if>" +
            "<if test='helpId != null'> AND help_id = #{helpId} AND order_id IS NULL</if>" +
            "</script>")
    int markRead(@Param("orderId") Long orderId, @Param("helpId") Long helpId, @Param("userId") Long userId);

    /** 未读私信数 */
    @Select("SELECT COUNT(*) FROM tb_chat_message WHERE receiver_id = #{userId} AND is_read = 0")
    int countUnread(@Param("userId") Long userId);

    /** 私信会话列表：每个 (help_id, order_id) 的最新一条消息 */
    @Select("SELECT m.* FROM tb_chat_message m WHERE m.id IN (" +
            "SELECT MAX(id) FROM tb_chat_message " +
            "WHERE sender_id = #{userId} OR receiver_id = #{userId} " +
            "GROUP BY CONCAT(COALESCE(help_id, ''), '-', COALESCE(order_id, ''))) " +
            "ORDER BY create_time DESC")
    List<ChatMessage> selectConversations(@Param("userId") Long userId);
}
