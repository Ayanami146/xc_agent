package com.xc.agent.mapper;

import com.xc.agent.model.po.RefreshTokenPO;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

public interface RefreshTokenMapper {
    RefreshTokenPO selectByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    int insert(RefreshTokenPO refreshToken);

    int rotate(@Param("tokenHash") String tokenHash,
               @Param("revokedAt") LocalDateTime revokedAt,
               @Param("replacedByTokenHash") String replacedByTokenHash);

    int revoke(@Param("tokenHash") String tokenHash, @Param("revokedAt") LocalDateTime revokedAt);
}
