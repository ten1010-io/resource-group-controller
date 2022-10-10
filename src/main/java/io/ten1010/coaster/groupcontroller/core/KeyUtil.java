package io.ten1010.coaster.groupcontroller.core;

public class KeyUtil {

    public static String buildKey(String namespace, String name) {
        return namespace + "/" + name;
    }

    public static String buildKey(String name) {
        return name;
    }

}
