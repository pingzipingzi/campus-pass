package com.example.campuspass.annotation;

import com.example.campuspass.annotation.constraint.ActivityTimeValidator;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ActivityTimeValidator.class)
@Documented//让注解可以生成在api文档当中
public @interface ValidActivityTime {

    //注解属性方法
    String message() default "活动时间顺序不合法";

    Class<?>[] groups()default {};

    Class<? extends Payload>[] payload() default {};
}
