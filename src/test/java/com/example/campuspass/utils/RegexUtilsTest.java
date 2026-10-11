package com.example.campuspass.utils;

import com.example.campuspass.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegexUtilsTest {

    @Test
    void validatesMainlandMobileNumberFormat() {
        assertFalse(RegexUtils.isPhoneInvalid("13800000000"));
        assertTrue(RegexUtils.isPhoneInvalid("12800000000"));
        assertTrue(RegexUtils.isPhoneInvalid("1380000000"));
        assertTrue(RegexUtils.isPhoneInvalid(null));
    }

    @Test
    void acceptsValidChineseAndEnglishNickNames() {
        assertDoesNotThrow(() -> RegexUtils.validateNickName("校园活动"));
        assertDoesNotThrow(() -> RegexUtils.validateNickName("CampusPass"));
    }

    @Test
    void rejectsWhitespaceAndInvalidWeightedLength() {
        assertThrows(BusinessException.class, () -> RegexUtils.validateNickName("Campus Pass"));
        assertThrows(BusinessException.class, () -> RegexUtils.validateNickName("中"));
    }
}
