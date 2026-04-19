package com.ahead.aiagent.utils;

import cn.hutool.crypto.digest.BCrypt;

public class PasswordUtils {

    public static String encode(String rawPassword) {
        return BCrypt.hashpw(rawPassword);
    }

    public static boolean matches(String rawPassword, String encodedPassword) {
        return BCrypt.checkpw(rawPassword, encodedPassword);
    }
}
