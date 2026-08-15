package com.greeniot.greensense.common.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Bật các job định kỳ: tưới theo lịch, quét cảm biến mất tín hiệu, ép tắt thiết bị quá
 * giờ, dọn lệnh không được ack, gộp thống kê ngày.
 *
 * <p>Tách khỏi class ứng dụng để <b>test tắt được</b>. Trước đây {@code @EnableScheduling}
 * nằm thẳng trên {@code GreenSenseApplication}, nên mọi test boot context thật đều chạy
 * kèm bộ lập lịch: {@code tick()} mỗi 60s và {@code timeoutSweep()} mỗi 30s chen vào giữa
 * các assertion và sửa đúng dữ liệu mà test đang kiểm. Trên máy dev bộ test chạy ~15s nên
 * hầu như không đụng; trên runner CI chậm hơn (~48s) thì thỉnh thoảng trúng — đúng kiểu
 * lỗi "chạy local thì xanh, lên CI thì đỏ" mà không đọc log thì không đoán ra.
 *
 * <p>Mặc định bật; chỉ {@code @IntegrationTest} mới tắt.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(prefix = "greensense.scheduling", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {
}
