package com.example.campuspass;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Encoders;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.crypto.SecretKey;

@SpringBootTest
class CampusPassApplicationTests {

    @Test
    void contextLoads() {
    }

    @Test
    void generateJwtKeys(){
        SecretKey key = Jwts.SIG.HS256.key().build();
        String secretBase64 = Encoders.BASE64.encode(key.getEncoded());
        System.out.println(secretBase64);
    }
    //原始密钥字节
    //    ↓ Base64 编码 转成base64编码是为了方便保存和传递，第一步生成的随机原始字节可能会有不可打印字符
    //配置中的字符串

    //配置中的字符串
    //    ↓ Base64 解码
    //原始密钥字节
    //    ↓
    //SecretKey
}
