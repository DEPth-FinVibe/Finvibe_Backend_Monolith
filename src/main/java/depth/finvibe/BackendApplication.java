package depth.finvibe;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.FullyQualifiedAnnotationBeanNameGenerator;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(
		nameGenerator = FullyQualifiedAnnotationBeanNameGenerator.class,
		scanBasePackages = "depth.finvibe"
)
@EnableKafka
@EnableScheduling
@EnableAsync
@EnableJpaAuditing
@ConfigurationPropertiesScan(basePackages = "depth.finvibe")
public class BackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(BackendApplication.class, args);
	}

}
