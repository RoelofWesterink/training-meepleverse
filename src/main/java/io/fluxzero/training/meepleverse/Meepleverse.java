package io.fluxzero.training.meepleverse;

import io.fluxzero.common.serialization.JsonUtils;
import io.fluxzero.sdk.Fluxzero;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@Slf4j
public class Meepleverse {
    public static void main(String[] args) {
        // start application
        SpringApplication app = new SpringApplication(Meepleverse.class);
        app.run(args);

        //initialize app
        initialize();
        log.info("Application initialized with test data");
    }

    static void initialize() {
        Fluxzero.sendAndForgetCommand(JsonUtils.fromFile("/catalog/register-items.json"));
    }
}
