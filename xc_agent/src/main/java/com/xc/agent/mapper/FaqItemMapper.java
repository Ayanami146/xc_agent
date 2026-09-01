package com.xc.agent.mapper;

import com.xc.agent.model.po.FaqItemPO;
import org.apache.ibatis.annotations.Param;
import java.time.LocalDateTime;
import java.util.List;

public interface FaqItemMapper {
    List<FaqItemPO> selectPage(@Param("keyword") String keyword, @Param("categoryId") Long categoryId,
                               @Param("status") String status, @Param("offset") int offset,
                               @Param("pageSize") int pageSize);
    long countPage(@Param("keyword") String keyword, @Param("categoryId") Long categoryId,
                   @Param("status") String status);
    List<FaqItemPO> selectPublishedPage(@Param("keyword") String keyword,
                                        @Param("categoryId") Long categoryId,
                                        @Param("offset") int offset,
                                        @Param("pageSize") int pageSize);
    long countPublishedPage(@Param("keyword") String keyword,
                            @Param("categoryId") Long categoryId);
    FaqItemPO selectPublishedByPublicId(@Param("publicId") String publicId);
    FaqItemPO selectByPublicId(@Param("publicId") String publicId);
    int insert(FaqItemPO item);
    int update(@Param("item") FaqItemPO item, @Param("expectedVersion") int expectedVersion);
    int updateStatus(@Param("id") Long id, @Param("status") String status,
                     @Param("publishedAt") LocalDateTime publishedAt,
                     @Param("expectedVersion") int expectedVersion);
}
