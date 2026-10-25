package com.ahead.aiagent.model.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 功能5：行程分享信息 VO
 */
@Data
public class ItineraryShareVO implements Serializable {

    /** 分享令牌（32 位随机 hex） */
    private String shareToken;

    /** 前端分享路径：/share/{shareToken} */
    private String sharePath;

    private static final long serialVersionUID = 1L;
}
