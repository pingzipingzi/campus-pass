package com.example.campuspass.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Result<T>{
    private Integer code;
    private String message;
    private T data;

    public static Result<Void> ok(){
        return new Result<>(0, "success", null);
    }

    public static <T>Result<T> ok(T data){
        return new Result<>(0, "success", data);
    }

    public static Result<Void> ok(String message){
        return new Result<>(0, message, null);
    }

    public static <T>Result<T> fail(Integer code,String message){
        return new Result<>(code, message, null);
    }

}
