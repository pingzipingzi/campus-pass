package com.example.campuspass.exception;

import com.example.campuspass.dto.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice//处理一次Http请求链的异常
public class GlobalExceptionHandler {
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleException(Exception e){
        log.error("未加入异常处理器的异常",e);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Result.fail(40000, "出现异常"));
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<Void>> handleBusinessException(BusinessException e) {
        String message = e.getMessage();
        Integer code = e.getCode();
        HttpStatus httpStatus = e.getHttpStatus();
        log.error("出现异常",e);
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
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(
                        Result.fail(
                                40000,
                                "传入数据非法"
                        )
                );
    }
}
