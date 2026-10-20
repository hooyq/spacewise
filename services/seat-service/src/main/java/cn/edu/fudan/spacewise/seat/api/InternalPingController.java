package cn.edu.fudan.spacewise.seat.api;

import cn.edu.fudan.spacewise.seat.api.dto.PingResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** 供其他服务探测连通性的内部接口（如 repair-service 的 /internal/connectivity）。 */
@RestController
public class InternalPingController {

    private final String serviceName;

    public InternalPingController(@Value("${spring.application.name}") String serviceName) {
        this.serviceName = serviceName;
    }

    @GetMapping("/internal/ping")
    public PingResponse ping() {
        return new PingResponse(serviceName);
    }
}
