package com.xc.agent.mapper;

import com.xc.agent.model.po.MessageCitationPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface MessageCitationMapper {

    int insertBatch(@Param("citations") List<MessageCitationPO> citations);

    List<MessageCitationPO> selectByMessageIds(List<Long> messageIds);
}
