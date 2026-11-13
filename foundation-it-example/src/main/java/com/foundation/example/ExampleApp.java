package com.foundation.example;

import com.github.f4b6a3.ulid.UlidCreator;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * BAS-23 示例工程入口：仅用于验证「只引 foundation-parent 即可编译与运行」。
 *
 * <p>依赖版本全部来自 foundation-parent 导入的 foundation-dependencies，本模块不写任何版本号。
 */
@SpringBootApplication
public class ExampleApp {

    public static void main(String[] args) {
        SpringApplication.run(ExampleApp.class, args);
    }

    /**
     * 用底座提供的 ULID 生成器产出一个带前缀的主键（00-施工总纲 §2.2：{前缀}_{ulid}）。
     */
    public static String newId(String prefix) {
        return prefix + "_" + UlidCreator.getMonotonicUlid();
    }
}
