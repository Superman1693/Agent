package com.ahead.aiagent.mapper;

import com.ahead.aiagent.model.entity.Recommendation;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 热门推荐 Mapper
 */
@Mapper
public interface RecommendationMapper extends BaseMapper<Recommendation> {
}
