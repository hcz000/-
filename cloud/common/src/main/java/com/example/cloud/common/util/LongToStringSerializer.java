package com.example.cloud.common.util;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;

import java.io.IOException;

/**
 * 仅用于序列化的 Long → 字符串序列化器，避免前端 JavaScript 处理大数字（如雪花 ID）时精度丢失。
 * <p>
 * 注意：只影响序列化（Java→JSON），反序列化和数据库操作保持 Long。
 */
public class LongToStringSerializer extends StdSerializer<Long> {

    public LongToStringSerializer() {
        super(Long.class);
    }

    @Override
    public void serialize(Long value, JsonGenerator gen, SerializerProvider provider) throws IOException {
        if (value == null) gen.writeNull();
        else gen.writeString(value.toString());
    }
}
