package org.cmb.teamcoordinator;

import org.cmb.teamcoordinator.config.DigitalTeamProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableConfigurationProperties(DigitalTeamProperties.class)
@EnableScheduling
public class TeamCoordinatorApplication {

    public static void main(String[] args) {
        SpringApplication.run(TeamCoordinatorApplication.class, args);
    }
}
