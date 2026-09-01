package com.xc.agent.mapper;

import com.xc.agent.model.po.ManualCategoryPO;
import org.apache.ibatis.annotations.Param;
import java.util.List;

public interface ManualCategoryMapper {
    List<ManualCategoryPO> selectAll();
    ManualCategoryPO selectByPublicId(@Param("publicId") String publicId);
    int insert(ManualCategoryPO category);
    int update(@Param("category") ManualCategoryPO category, @Param("expectedVersion") int expectedVersion);
    int delete(@Param("id") Long id, @Param("expectedVersion") int expectedVersion);
    long countItems(@Param("id") Long id);
}
