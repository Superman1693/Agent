package com.ahead.aiagent.service;

import com.ahead.aiagent.model.dto.DestinationReviewSaveRequest;
import com.ahead.aiagent.model.dto.DestinationReviewUpdateRequest;
import com.ahead.aiagent.model.vo.DestinationReviewPageVO;
import com.ahead.aiagent.model.vo.DestinationReviewVO;

/**
 * 功能6：目的地用户评价服务
 */
public interface DestinationReviewService {

    /**
     * 新增/更新评价（同用户同目的地幂等）
     *
     * @param userId 登录用户（控制器层已校验非空）
     */
    DestinationReviewVO saveReview(DestinationReviewSaveRequest request, Long userId);

    /**
     * 按评价 ID 更新（需归属校验）
     */
    DestinationReviewVO updateReview(Long id, DestinationReviewUpdateRequest request, Long userId);

    /**
     * 按评价 ID 删除（需归属校验）
     */
    boolean deleteReview(Long id, Long userId);

    /**
     * 公开分页查询目的地评价（userId 可空：带有效登录态时填充 mine）
     */
    DestinationReviewPageVO listReviews(Long destinationId, Integer page, Integer size, Long userId);
}
