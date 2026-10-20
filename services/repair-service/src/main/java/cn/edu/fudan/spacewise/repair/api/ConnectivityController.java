package cn.edu.fudan.spacewise.repair.api;

import cn.edu.fudan.spacewise.repair.service.ConnectivityService;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 返回到各下游服务的连通性，如 {"seat-service":"UP"}。
 * /internal/connectivity 供容器网络内的冒烟测试使用；/api/repair/connectivity 经网关对外，
 * 用于演示和验证“网关 → repair → seat”整条链路的跨服务调用与 traceId 传递。
 */
@RestController
public class ConnectivityController {

    private final ConnectivityService connectivityService;

    public ConnectivityController(ConnectivityService connectivityService) {
        this.connectivityService = connectivityService;
    }

    @GetMapping({"/internal/connectivity", "/api/repair/connectivity"})
    public Map<String, String> connectivity() {
        return connectivityService.check();
    }
}
