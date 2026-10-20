package cn.edu.fudan.spacewise.common.web;

import io.micrometer.tracing.Tracer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;

/**
 * 服务引入 web-common 依赖后自动生效（登记在 META-INF/spring/...AutoConfiguration.imports），
 * 不需要在服务里扫描本包或写任何配置。
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class WebCommonAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    ErrorResponses errorResponses(ObjectProvider<Tracer> tracer) {
        return new ErrorResponses(tracer);
    }

    @Bean
    @ConditionalOnMissingBean
    GlobalExceptionHandler globalExceptionHandler(ErrorResponses errorResponses) {
        return new GlobalExceptionHandler(errorResponses);
    }
}
