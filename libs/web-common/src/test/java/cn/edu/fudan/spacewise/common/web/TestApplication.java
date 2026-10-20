package cn.edu.fudan.spacewise.common.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** 只用于测试的最小应用：模拟一个引入了 web-common 的服务，各接口分别抛出不同类型的异常。 */
@SpringBootApplication
class TestApplication {

    enum TestErrorCode implements ErrorCode {
        THING_NOT_FOUND(HttpStatus.NOT_FOUND);

        private final HttpStatus status;

        TestErrorCode(HttpStatus status) {
            this.status = status;
        }

        @Override
        public String code() {
            return "TEST." + name();
        }

        @Override
        public HttpStatus status() {
            return status;
        }
    }

    record CreateRequest(@NotBlank(message = "不能为空") String name) {
    }

    @RestController
    static class ThrowingController {

        @GetMapping("/test/business")
        String business() {
            throw new BusinessException(TestErrorCode.THING_NOT_FOUND, "东西不存在");
        }

        @PostMapping("/test/body")
        String body(@Valid @RequestBody CreateRequest request) {
            return request.name();
        }

        @GetMapping("/test/param/{code}")
        String param(@PathVariable @Pattern(regexp = "[A-Z0-9]+", message = "只能是大写字母和数字") String code) {
            return code;
        }

        @GetMapping("/test/boom")
        String boom() {
            throw new IllegalStateException("SELECT * FROM secret_table WHERE password = 'x'");
        }
    }
}
