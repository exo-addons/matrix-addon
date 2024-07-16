package org.exoplatform.addons.matrix;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.PropertySource;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import io.meeds.spring.AvailableIntegration;
import io.meeds.spring.kernel.PortalApplicationContextInitializer;

@SpringBootApplication(scanBasePackages = {
        MatrixApplication.MODULE_NAME,
        AvailableIntegration.KERNEL_MODULE,
        AvailableIntegration.WEB_MODULE,
})
@EnableJpaRepositories(basePackages = MatrixApplication.MODULE_NAME)
@PropertySource("classpath:application.properties")
@PropertySource("classpath:application-common.properties")
@PropertySource("classpath:matrix.properties")
public class MatrixApplication extends PortalApplicationContextInitializer {
  public static final String MODULE_NAME = "org.exoplatform.addons.matrix";
}
