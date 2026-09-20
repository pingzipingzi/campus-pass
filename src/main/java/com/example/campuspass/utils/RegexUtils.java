package com.example.campuspass.utils;

import cn.hutool.core.util.StrUtil;

import static com.example.campuspass.utils.RegexConstants.PHONE_REGEX;

public class RegexUtils {
    public static boolean isPhoneInvalid(String phone){
        return misMatch(phone, PHONE_REGEX);
    }

    public static boolean misMatch(String str,String regex) {
        if (StrUtil.isBlank(str)) {
            return true;
        }
        return (!str.matches(regex));
    }
}
