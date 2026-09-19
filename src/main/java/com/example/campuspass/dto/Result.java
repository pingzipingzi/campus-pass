package com.example.campuspass.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Result {
    private Integer code;
    private String message;
    private Object data;

    public static Result ok(){
        return new Result(0, "success", null);
    }

    public static Result ok(Object data){
        return new Result(0, "success", data);
    }

    public static Result fail(String message){
        return new Result(1000,message,null);
    }

    public static Result fail(Integer code,String message){
        return new Result(code, message, null);

    }
}
