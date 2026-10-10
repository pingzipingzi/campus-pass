package com.example.campuspass.exception;

import com.example.campuspass.dto.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.http.converter.HttpMessageNotReadableException;

import static com.example.campuspass.exception.ErrorCode.INTERNAL_ERROR;
import static com.example.campuspass.exception.ErrorCode.VALIDATION_FAILED;

@Slf4j
@RestControllerAdvice//处理一次Http请求链的异常
public class GlobalExceptionHandler {
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleException(Exception e){
        log.error("未加入异常处理器的异常",e);
        return ResponseEntity.status(INTERNAL_ERROR.getHttpStatus())
                .body(Result.fail(INTERNAL_ERROR.getCode(), INTERNAL_ERROR.getMessage()));
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<Void>> handleBusinessException(BusinessException e) {
        String message = e.getMessage();
        Integer code = e.getCode();
        HttpStatus httpStatus = e.getHttpStatus();
        log.warn("业务异常，code={}，message={}", code, message);
        return ResponseEntity.status(httpStatus)
                .body(
                        Result.fail(
                                code,
                        message
                                )
                );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Void>> handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        log.warn("输入数据非法");
        return validationFailed();
    }

    @ExceptionHandler({
            HandlerMethodValidationException.class,
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class
    })
    public ResponseEntity<Result<Void>> handleRequestValidationException(Exception e) {
        log.warn("请求参数校验失败：{}", e.getClass().getSimpleName());
        return validationFailed();
    }

    private ResponseEntity<Result<Void>> validationFailed() {
        return ResponseEntity.status(VALIDATION_FAILED.getHttpStatus())
                .body(Result.fail(VALIDATION_FAILED.getCode(), "传入数据非法"));
    }
}
