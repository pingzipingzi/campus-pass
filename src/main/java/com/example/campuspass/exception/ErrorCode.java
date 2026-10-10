package com.example.campuspass.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * 统一业务错误码。
 *
 * <p>编码规则：</p>
 * <ul>
 *     <li>400xx：请求参数错误</li>
 *     <li>401xx：登录与凭证错误</li>
 *     <li>403xx：权限错误</li>
 *     <li>404xx：资源不存在</li>
 *     <li>409xx：业务状态冲突</li>
 *     <li>429xx：访问频率限制</li>
 *     <li>500xx：服务端处理错误</li>
 *     <li>503xx：服务暂时不可用</li>
 * </ul>
 */
@Getter
public enum ErrorCode {

    PARAMETER_INVALID(40000, "请求参数有误", HttpStatus.BAD_REQUEST),
    VALIDATION_FAILED(40001, "参数校验失败", HttpStatus.BAD_REQUEST),

    AUTH_REQUIRED(40100, "请先登录", HttpStatus.UNAUTHORIZED),
    INVALID_CREDENTIALS(40101, "账号或凭证有误", HttpStatus.UNAUTHORIZED),
    INVALID_TOKEN(40102, "登录凭证无效", HttpStatus.UNAUTHORIZED),
    ACCOUNT_DISABLED(40103, "账号已被禁用", HttpStatus.UNAUTHORIZED),
    VERIFICATION_CODE_INVALID(40104, "验证码无效", HttpStatus.UNAUTHORIZED),

    FORBIDDEN(40300, "无权执行此操作", HttpStatus.FORBIDDEN),
    NOT_RESOURCE_OWNER(40301, "无权操作该资源", HttpStatus.FORBIDDEN),
    ADMIN_OPERATION_FORBIDDEN(40302, "不允许执行该管理员操作", HttpStatus.FORBIDDEN),

    USER_NOT_FOUND(40400, "用户不存在", HttpStatus.NOT_FOUND),
    ACTIVITY_NOT_FOUND(40401, "活动不存在", HttpStatus.NOT_FOUND),
    REQUEST_NOT_FOUND(40402, "请求不存在", HttpStatus.NOT_FOUND),

    RESOURCE_CONFLICT(40900, "资源已存在", HttpStatus.CONFLICT),
    ACTIVITY_STATE_CONFLICT(40901, "活动状态不允许此操作", HttpStatus.CONFLICT),
    REGISTRATION_CLOSED(40902, "当前不在报名时间内", HttpStatus.CONFLICT),
    ALREADY_REGISTERED(40903, "用户已经报名", HttpStatus.CONFLICT),
    NOT_REGISTERED(40904, "用户当前没有有效报名", HttpStatus.CONFLICT),
    NOT_WAITLISTED(40905, "用户当前不在候补中", HttpStatus.CONFLICT),
    CAPACITY_CONFLICT(40906, "活动容量不允许此操作", HttpStatus.CONFLICT),
    DATA_STATE_CONFLICT(40907, "数据状态已变化", HttpStatus.CONFLICT),

    TOO_MANY_REQUESTS(42900, "请求过于频繁", HttpStatus.TOO_MANY_REQUESTS),

    INTERNAL_ERROR(50000, "服务器内部错误", HttpStatus.INTERNAL_SERVER_ERROR),
    DATA_OPERATION_FAILED(50001, "数据操作失败", HttpStatus.INTERNAL_SERVER_ERROR),
    CACHE_OPERATION_FAILED(50002, "缓存操作失败", HttpStatus.INTERNAL_SERVER_ERROR),
    MESSAGE_PROCESSING_FAILED(50003, "消息处理失败", HttpStatus.INTERNAL_SERVER_ERROR),
    DATA_INCONSISTENT(50004, "数据状态异常", HttpStatus.INTERNAL_SERVER_ERROR),

    SERVICE_BUSY(50300, "服务繁忙，请稍后重试", HttpStatus.SERVICE_UNAVAILABLE),
    REQUEST_INTERRUPTED(50301, "请求已中断，请稍后重试", HttpStatus.SERVICE_UNAVAILABLE);

    private final int code;
    private final String message;
    private final HttpStatus httpStatus;

    ErrorCode(int code, String message, HttpStatus httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }
}
