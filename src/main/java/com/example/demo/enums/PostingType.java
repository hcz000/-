package com.example.demo.enums;

/**
 * 帖子类型枚举
 * 每种类型对应一个固定向量，用于兴趣推荐
 * 8种类型分别对应向量 [1,0,0,0,0,0,0,0], [0,1,0,0,0,0,0,0], ...
 */
public enum PostingType {
    Chat_about_life(0, "闲聊生活"),       // [1,0,0,0,0,0,0,0]
    Sport_and_fitness(1, "运动健身"),     // [0,1,0,0,0,0,0,0]
    Food_exploration(2, "美食探索"),      // [0,0,1,0,0,0,0,0]
    Game_and_esports(3, "游戏电竞"),      // [0,0,0,1,0,0,0,0]
    Movie_and_art(4, "影音书画"),         // [0,0,0,0,1,0,0,0]
    Photography(5, "摄影"),               // [0,0,0,0,0,1,0,0]
    Technology(6, "科技数码"),            // [0,0,0,0,0,0,1,0]
    Travel(7, "旅行户外");                // [0,0,0,0,0,0,0,1]

    // 固定向量，索引位置为1，其他为0
    private final float[] vector;
    private final int index;
    private final String typeName;

    PostingType(int index, String typeName) {
        this.index = index;
        this.typeName = typeName;
        this.vector = createVector(index);
    }

    /**
     * 创建固定向量：指定索引位置为1，其他为0
     */
    private static float[] createVector(int index) {
        float[] vec = new float[8];
        vec[index] = 1.0f;
        return vec;
    }

    public float[] getVector() {
        return vector;
    }

    public int getIndex() {
        return index;
    }

    public String getTypeName() {
        return typeName;
    }

    /**
     * 根据类型名称获取枚举
     */
    public static PostingType fromTypeName(String typeName) {
        for (PostingType type : values()) {
            if (type.getTypeName().equals(typeName)) {
                return type;
            }
        }
        return null;
    }

    /**
     * 根据名称或类型名称获取枚举
     */
    public static PostingType fromString(String name) {
        for (PostingType type : values()) {
            if (type.name().equalsIgnoreCase(name) || type.getTypeName().equals(name)) {
                return type;
            }
        }
        return null;
    }

    /**
     * 向量维度（固定为8）
     */
    public static final int VECTOR_DIMENSION = 8;
}