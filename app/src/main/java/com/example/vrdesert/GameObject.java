package com.example.vrdesert;

public class GameObject {
    public enum Type { EDIBLE_MUSHROOM, CAVE_PLANT, TOXIC_FUNGUS, INFO_BUTTON_0, INFO_BUTTON_1, INFO_BUTTON_2 }
    
    public float x;
    public float y;
    public float z;
    public boolean isCollected;
    public Type type;

    public GameObject(float x, float y, float z, Type type) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.type = type;
        this.isCollected = false;
    }
}
