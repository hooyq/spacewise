package cn.edu.fudan.spacewise.repair.service;

import cn.edu.fudan.spacewise.repair.client.SeatClient;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 检查本服务到下游服务的网络连通性，用于部署后的冒烟测试和演示跨服务调用。
 * 下游不可用只体现在这里的结果中，不影响本服务自身的 /actuator/health，避免故障沿调用链扩散。
 */
@Service
public class ConnectivityService {

    private static final Logger log = LoggerFactory.getLogger(ConnectivityService.class);

    private final SeatClient seatClient;

    public ConnectivityService(SeatClient seatClient) {
        this.seatClient = seatClient;
    }

    public Map<String, String> check() {
        Map<String, String> result = new LinkedHashMap<>();
        result.put("seat-service", pingSeat());
        return result;
    }

    private String pingSeat() {
        try {
            seatClient.ping();
            return "UP";
        } catch (RuntimeException e) {
            log.warn("seat-service 不可达: {}", e.getMessage());
            return "DOWN";
        }
    }
}
