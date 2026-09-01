package com.xc.agent.mapper;

import com.xc.agent.model.po.ManualDocPO;
import org.apache.ibatis.annotations.Param;
import java.time.LocalDateTime;
import java.util.List;

public interface ManualDocMapper {
    List<ManualDocPO> selectPage(@Param("keyword") String keyword, @Param("categoryId") Long categoryId,
                                 @Param("status") String status, @Param("offset") int offset,
                                 @Param("pageSize") int pageSize);
    long countPage(@Param("keyword") String keyword, @Param("categoryId") Long categoryId,
                   @Param("status") String status);
    List<ManualDocPO> selectPublishedForRag();
    ManualDocPO selectByPublicId(@Param("publicId") String publicId);
    int insert(ManualDocPO doc);
    int updateMetadata(@Param("doc") ManualDocPO doc, @Param("expectedVersion") int expectedVersion);
    int replaceFile(@Param("doc") ManualDocPO doc, @Param("expectedVersion") int expectedVersion);
    int updateStatus(@Param("id") Long id, @Param("status") String status,
                     @Param("publishedAt") LocalDateTime publishedAt,
                     @Param("expectedVersion") int expectedVersion);
}
