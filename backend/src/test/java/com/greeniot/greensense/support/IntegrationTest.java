package com.greeniot.greensense.support;

import de.flapdoodle.embed.mongo.spring.autoconfigure.EmbeddedMongoAutoConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * One annotation for every integration test, so the embedded-Mongo and disabled-broker
 * setup lives in a single place and Spring can reuse one application context across the
 * whole suite instead of booting a fresh one per class.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest
@AutoConfigureMockMvc
@ImportAutoConfiguration(EmbeddedMongoAutoConfiguration.class)
@TestPropertySource(properties = {
        "greensense.mqtt.enabled=false",
        "greensense.seed.enabled=false",
        "greensense.weather.enabled=false",
        // Tắt bộ lập lịch: nếu để bật, tick()/timeoutSweep()/enforceAutoOff() chạy nền
        // và sửa đúng dữ liệu mà test đang kiểm. Test nào cần chúng thì gọi thẳng method.
        "greensense.scheduling.enabled=false",
        "de.flapdoodle.mongodb.embedded.version=7.0.4"
})
public @interface IntegrationTest {
}
