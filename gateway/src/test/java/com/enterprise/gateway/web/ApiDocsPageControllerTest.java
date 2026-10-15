package com.enterprise.gateway.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

class ApiDocsPageControllerTest {

    @Test
    void onRenderThePageWakesEveryServiceAndThenOpensSwagger() {
        ApiDocsPageController controller = new ApiDocsPageController(
                List.of("https://enterprise-auth.onrender.com", " https://enterprise-order.onrender.com ", ""));

        ResponseEntity<String> response = controller.docs();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.TEXT_HTML);
        assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");
        assertThat(response.getBody())
                .contains("const WAKE_URLS = [\"https://enterprise-auth.onrender.com\", "
                        + "\"https://enterprise-order.onrender.com\"];")
                .contains("\"/api-docs/\" + id")
                .contains("location.replace(\"/swagger-ui.html\")")
                .doesNotContain("__WAKE_URLS__", "__SWAGGER_UI__");
    }

    @Test
    void withoutServicesToWakeItRedirectsToSwagger() {
        ResponseEntity<String> response = new ApiDocsPageController(List.of()).docs();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        assertThat(response.getHeaders().getLocation()).hasToString("/swagger-ui.html");
    }

    @Test
    void addressesCannotBreakOutOfTheScript() {
        assertThat(ApiDocsPageController.jsString("https://x.example/</script><script>alert(\"1\")"))
                .isEqualTo("\"https://x.example/\\u003c/script\\u003e\\u003cscript\\u003ealert(\\u00221\\u0022)\"");
    }
}
