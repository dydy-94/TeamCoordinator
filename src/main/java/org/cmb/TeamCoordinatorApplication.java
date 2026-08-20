package org.cmb;

import org.cmb.common.config.DigitalTeamProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

// Mapper interfaces are discovered via their @Mapper annotation; the
// starter's auto-scan follows scanBasePackages, which covers both the
// legacy and the DDD-layered mapper packages.
@SpringBootApplication(scanBasePackages = "org.cmb")
@EnableConfigurationProperties(DigitalTeamProperties.class)
@EnableScheduling
public class TeamCoordinatorApplication {

    public static void main(String[] args) {
        SpringApplication.run(TeamCoordinatorApplication.class, args);
    }
}
