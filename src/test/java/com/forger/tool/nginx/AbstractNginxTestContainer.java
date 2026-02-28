package com.forger.tool.nginx;

import com.forger.tool.timing.TestCronometer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testcontainers.containers.wait.strategy.HttpWaitStrategy;
import org.testcontainers.nginx.NginxContainer;
import org.testcontainers.utility.MountableFile;

import java.nio.file.Path;

@ExtendWith(TestCronometer.class)
public abstract class AbstractNginxTestContainer {

    private static final String NGINX_BASE_RESOURCE_PATH = "/usr/share/nginx/html/";

    protected static NginxContainer nginxContainer;

    @BeforeAll
    static void setup() {
        nginxContainer = new NginxContainer(TestContainerImages.NGINX.getImage())
                .waitingFor(new HttpWaitStrategy());

        configureResources();

        nginxContainer.start();
    }

    @AfterAll
    static void tearDown() {
        nginxContainer.stop();
    }

    protected String getNginxUrl() {
        return "http://" +
                nginxContainer.getHost() + ":" +
                nginxContainer.getMappedPort(80);
    }

    private static void configureResources() {
        // Set image resource
        Path imageResource = Path.of(
                "src/test/resources/container/nginx/image/black.png");

        nginxContainer.withCopyFileToContainer(
                MountableFile.forHostPath(imageResource), NGINX_BASE_RESOURCE_PATH + "images/black.png");

        // Set video resource
        Path videoResource = Path.of(
                "src/test/resources/container/nginx/video/black_and_white.mp4");

        nginxContainer.withCopyFileToContainer(
                MountableFile.forHostPath(videoResource), NGINX_BASE_RESOURCE_PATH + "video/black_and_white.mp4");

        // Set base HTML
        Path htmlResource = Path.of(
                "src/test/resources/container/nginx/nginx.html");

        nginxContainer.withCopyFileToContainer(
                MountableFile.forHostPath(htmlResource), NGINX_BASE_RESOURCE_PATH + "index.html");
    }
}
