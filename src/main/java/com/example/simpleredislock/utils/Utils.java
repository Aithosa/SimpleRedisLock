package com.example.simpleredislock.utils;

import java.nio.ByteBuffer;
import java.util.Base64;
import java.util.UUID;

public class Utils {

    public Utils() {
    }

    /**
     * 生成UUID并转换为Base64格式
     *
     * @return 生成的id
     */
    public static String uuidBase64() {
        UUID uuid = UUID.randomUUID();

        byte[] src = ByteBuffer.wrap(new byte[16])
                .putLong(uuid.getMostSignificantBits())
                .putLong(uuid.getLeastSignificantBits())
                .array();

        return Base64.getEncoder().encodeToString(src).substring(0, 22);
    }
}
