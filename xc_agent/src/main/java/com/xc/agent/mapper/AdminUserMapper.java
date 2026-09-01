package com.xc.agent.mapper;

import com.xc.agent.model.po.AdminUserPO;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface AdminUserMapper {
    AdminUserPO selectByUsername(@Param("username") String username);
    AdminUserPO selectById(@Param("id") Long id);
    AdminUserPO selectByPublicId(@Param("publicId") String publicId);
    List<AdminUserPO> selectPage(@Param("keyword") String keyword, @Param("role") String role,
                                 @Param("status") String status, @Param("offset") int offset,
                                 @Param("pageSize") int pageSize);
    long countPage(@Param("keyword") String keyword, @Param("role") String role,
                   @Param("status") String status);
    int updatePasswordHash(@Param("id") Long id, @Param("passwordHash") String passwordHash);
    int recordLoginFailure(@Param("id") Long id, @Param("lockedUntil") LocalDateTime lockedUntil,
                           @Param("threshold") int threshold);
    int resetLoginFailures(@Param("id") Long id);
}
