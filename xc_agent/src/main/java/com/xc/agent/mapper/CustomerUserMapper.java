package com.xc.agent.mapper;

import com.xc.agent.model.po.CustomerUserPO;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

public interface CustomerUserMapper {
    CustomerUserPO selectByAccount(@Param("account") String account);

    CustomerUserPO selectByPhoneHash(@Param("phoneHash") String phoneHash);

    CustomerUserPO selectById(@Param("id") Long id);

    int insert(CustomerUserPO user);

    int updateLastLoginAt(@Param("id") Long id, @Param("lastLoginAt") LocalDateTime lastLoginAt);

    int updatePasswordHash(@Param("id") Long id, @Param("passwordHash") String passwordHash);
}
