package cn.edu.fudan.spacewise.repair.api;

import cn.edu.fudan.spacewise.repair.service.ConnectivityService;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** 内部接口：返回到各下游服务的连通性，如 {"seat-service":"UP"}。 */
@RestController
public class ConnectivityController {

    private final ConnectivityService connectivityService;

    public ConnectivityController(ConnectivityService connectivityService) {
        this.connectivityService = connectivityService;
    }

    @GetMapping("/internal/connectivity")
    public Map<String, String> connectivity() {
        return connectivityService.check();
    }
}
