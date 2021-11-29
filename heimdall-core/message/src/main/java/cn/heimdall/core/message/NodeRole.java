package cn.heimdall.core.message;

public enum NodeRole {
    CLIENT(0),
    COMPUTE(1),
    STORAGE(2),
    GUARDER(3);
    private int value;

    NodeRole(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }
}
