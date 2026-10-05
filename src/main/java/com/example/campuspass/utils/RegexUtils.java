package com.example.campuspass.utils;

import cn.hutool.core.util.StrUtil;

import static com.example.campuspass.exception.BusinessException.error;
import static com.example.campuspass.utils.RegexConstants.NICK_NAME_REGEX;
import static com.example.campuspass.utils.RegexConstants.PHONE_REGEX;

public class RegexUtils {
    public static boolean isPhoneInvalid(String phone){
        return misMatch(phone, PHONE_REGEX);
    }

    public static void validateNickName(String userName ){
        if (!userName.matches(NICK_NAME_REGEX)) {
            error("用户名不能包含空白字符、换行符或控制字符");
        }
        int sum = userName.codePoints()
                .map(cp -> Character.UnicodeScript.of(cp)
                        == Character.UnicodeScript.HAN ? 2 : 1).sum();
        if (sum < 4 || sum > 20) {
            error( "名字过长或过短，中文2~10，英文4~20");
        }
    }

    public static boolean misMatch(String str,String regex) {
        if (StrUtil.isBlank(str)) {
            return true;
        }
        return (!str.matches(regex));
    }
}
