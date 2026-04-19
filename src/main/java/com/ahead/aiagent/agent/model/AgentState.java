package com.ahead.aiagent.agent.model;

/*
 * @description: 智能助手状态枚举
 */
public enum AgentState {

    /**
     * 空闲状态
     */
    IDLE,

    /**
     * 处理中状态
     */
    RUNNING,

    /**
     * 处理完成状态
     */
    FINISHED,

    /**
     * 处理错误状态
     */
    ERROR
}
