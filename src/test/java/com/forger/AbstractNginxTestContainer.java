package com.forger;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.testcontainers.containers.wait.strategy.HttpWaitStrategy;
import org.testcontainers.nginx.NginxContainer;
import org.testcontainers.utility.MountableFile;

import java.nio.file.Path;

@QuarkusTest
public abstract class AbstractNginxTestContainer {

    static NginxContainer nginxContainer;

    @BeforeAll
    static void setup() {
        Path resourcesDirectory = Path.of(
                "src/test/resources/container/nginx.html");

        nginxContainer = new NginxContainer(TestContainerImages.NGINX.getImage())
                .withCopyFileToContainer(MountableFile.forHostPath(resourcesDirectory), "/usr/share/nginx/html")
                .waitingFor(new HttpWaitStrategy());

        nginxContainer.start();
    }

    @AfterAll
    static void tearDown() {
        nginxContainer.stop();
    }
}
