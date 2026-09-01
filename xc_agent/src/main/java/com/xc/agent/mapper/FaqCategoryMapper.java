package com.xc.agent.mapper;

import com.xc.agent.model.po.FaqCategoryPO;
import org.apache.ibatis.annotations.Param;
import java.util.List;

public interface FaqCategoryMapper {
    List<FaqCategoryPO> selectAll();
    List<FaqCategoryPO> selectEnabled();
    FaqCategoryPO selectByPublicId(@Param("publicId") String publicId);
    int insert(FaqCategoryPO category);
    int update(@Param("category") FaqCategoryPO category, @Param("expectedVersion") int expectedVersion);
    int delete(@Param("id") Long id, @Param("expectedVersion") int expectedVersion);
    int updateOrder(@Param("id") Long id, @Param("sortOrder") int sortOrder,
                    @Param("expectedVersion") int expectedVersion);
    long countItems(@Param("id") Long id);
}
