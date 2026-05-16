package com.example.demo.config;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;

import java.io.IOException;

/**
 * 仅用于序列化的 Long 转字符串序列化器
 * 避免前端 JavaScript 处理大数字时精度丢失
 * 注意：这个序列化器只影响序列化（Java对象→JSON输出），不影响反序列化和数据库操作
 */
public class LongToStringSerializer extends StdSerializer<Long> {

    public LongToStringSerializer() {
        super(Long.class);
    }

    @Override
    public void serialize(Long value, JsonGenerator gen, SerializerProvider provider) throws IOException {
        if (value == null) {
            gen.writeNull();
        } else {
            // 直接转换为字符串，不添加千分位分隔符
            gen.writeString(value.toString());
        }
    }
}