package cn.edu.fudan.spacewise.repair.service;

import static org.assertj.core.api.Assertions.assertThat;

import cn.edu.fudan.spacewise.repair.client.SeatClient;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;

/** 用手写的假 SeatClient 代替真实调用，只验证 ConnectivityService 自己的判断逻辑。 */
class ConnectivityServiceTest {

    @Test
    void seatReachableIsUp() {
        SeatClient reachable = () -> new SeatClient.SeatPing("seat-service");

        assertThat(new ConnectivityService(reachable).check()).isEqualTo(Map.of("seat-service", "UP"));
    }

    @Test
    void seatUnreachableIsDownInsteadOfFailing() {
        SeatClient unreachable = () -> {
            throw new ResourceAccessException("Connection refused");
        };

        assertThat(new ConnectivityService(unreachable).check()).isEqualTo(Map.of("seat-service", "DOWN"));
    }
}
