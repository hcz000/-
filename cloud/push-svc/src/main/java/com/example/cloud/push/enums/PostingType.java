package com.example.cloud.push.enums;

/**
 * 帖子类型枚举
 * 每种类型对应一个固定向量，用于兴趣推荐
 * 8 种类型分别对应 8 维 one-hot 向量
 */
public enum PostingType {
    Chat_about_life(0, "闲聊生活"),
    Sport_and_fitness(1, "运动健身"),
    Food_exploration(2, "美食探索"),
    Game_and_esports(3, "游戏电竞"),
    Movie_and_art(4, "影音书画"),
    Photography(5, "摄影"),
    Technology(6, "科技数码"),
    Travel(7, "旅行户外");

    private final float[] vector;
    private final int index;
    private final String typeName;

    PostingType(int index, String typeName) {
        this.index = index;
        this.typeName = typeName;
        this.vector = createVector(index);
    }

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

    public static PostingType fromTypeName(String typeName) {
        for (PostingType type : values()) {
            if (type.getTypeName().equals(typeName)) {
                return type;
            }
        }
        return null;
    }

    public static final int VECTOR_DIMENSION = 8;
}
