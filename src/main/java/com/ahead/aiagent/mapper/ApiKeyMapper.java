package com.ahead.aiagent.mapper;

import com.ahead.aiagent.model.entity.ApiKey;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ApiKeyMapper extends BaseMapper<ApiKey> {

    /** 原子递增已用配额，同时校验未超限（安全修复：传入的是 key 的 SHA-256 摘要） */
    @Update("UPDATE api_key SET used_quota = used_quota + 1, update_time = NOW() " +
            "WHERE api_key = #{apiKeyHash} AND status = 1 AND used_quota < total_quota")
    int incrementUsedQuota(String apiKeyHash);
}
