package com.example.campuspass.utils;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum ActivityTypeEnum {

    LECTURE(0,"讲座"),
    COMPETITION(1, "竞赛"),
    SPORTS(2,"体育活动"),
    VOLUNTEER(3,"志愿活动"),
    CLUB(4,"社团/文娱活动"),
    OTHER(5,"其他");

    private final Integer code;
    private final String description;

    public static String getDescription(Integer code) {
        for (ActivityTypeEnum type : values()) {
            if (type.getCode().equals(code)) {
                return type.getDescription();
            }
        }
        return null;
    }
}
