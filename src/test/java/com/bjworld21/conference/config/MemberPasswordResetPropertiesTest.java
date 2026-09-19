package com.bjworld21.conference.config;

import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class MemberPasswordResetPropertiesTest {
    @Test
    void onlyLocalHttpAndConfiguredHttpsOriginsCanBeUsedInEmail() {
        var properties = new MemberPasswordResetProperties();
        assertThat(properties.validatedBaseUrl()).isEqualTo("http://localhost:8080");
        properties.setPublicBaseUrl("https://conference.example/");
        assertThat(properties.validatedBaseUrl()).isEqualTo("https://conference.example");
        for (String invalid : List.of("http://conference.example", "https://user@conference.example",
                "https://conference.example?next=evil", "https://conference.example#token=x",
                "https://conference.example/subpath", "https://bad host", "//conference.example", "")) {
            properties.setPublicBaseUrl(invalid);
            assertThatThrownBy(properties::validatedBaseUrl).isInstanceOf(IllegalStateException.class);
        }
    }

    @Test
    void expirationMustBeBetweenOneMinuteAndOneHour() {
        var properties = new MemberPasswordResetProperties();
        for (Duration invalid : List.of(Duration.ZERO, Duration.ofSeconds(30), Duration.ofHours(2))) {
            properties.setTokenTtl(invalid);
            assertThatThrownBy(properties::validatedBaseUrl).isInstanceOf(IllegalStateException.class);
        }
    }
}
