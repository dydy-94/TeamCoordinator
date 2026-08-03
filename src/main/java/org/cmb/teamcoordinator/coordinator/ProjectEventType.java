package org.cmb.teamcoordinator.coordinator;

public enum ProjectEventType {
    COORDINATOR_ANALYZING, // 协调器接受了消息
    PLAN_CREATED,  // 计划创建
    PLAN_REVISED,  // 计划修订
    TASK_STARTED,  // 任务开始
    TASK_PROGRESS_UPDATED,  // 任务进度更新
    TASK_WAITING_HUMAN,  // 任务等待人工干预
    TASK_SUCCEEDED,  // 任务成功
    TASK_FAILED,  // 任务失败
    ARTIFACT_CREATED,  // 产物创建
    FINAL_RESPONSE,  // 最终响应
    MESSAGE_ACCEPTED_INTERNAL,  // 消息被协调器接受（内部事件）
}
