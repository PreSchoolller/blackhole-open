package io.github.preschoolller.blackhole.utils;

import java.io.File;

public class Constants {

    /**
     * 非 src/main/resources 资源目录名
     */
    public static final String RESOURCES = "resources";

    /**
     * 非 src/main/resources 资源目录
     */
    public static final String RESOURCES_DIR = RESOURCES + "/";

    /**
     * 非 src/main/resources 着色器目录名
     */
    public static final String SHADERS = RESOURCES_DIR + "shaders";

    /**
     * 非 src/main/resources 着色器目录
     */
    public static final String SHADERS_DIR = SHADERS + "/";

    /**
     * 着色器缓存目录，项目根目录，jar根目录名
     */
    public static final String SHADER_CACHE = "shader-cache";

    /**
     * 着色器缓存目录，项目根目录，jar根目录
     */
    public static final String SHADER_CACHE_DIR = SHADER_CACHE + File.separator;
}