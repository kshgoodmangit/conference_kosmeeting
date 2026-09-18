package com.bjworld21.congress.publicsite;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.public-site")
@Getter
@Setter
public class PublicSiteProperties {
    public enum ConferenceMode { SINGLE, MULTI }
    private ConferenceMode conferenceMode = ConferenceMode.MULTI;
    private Long defaultConferenceSeq;

    public boolean isMulti() { return conferenceMode == ConferenceMode.MULTI; }

    @PostConstruct
    public void validate() {
        if (conferenceMode == null || (!isMulti() && (defaultConferenceSeq == null || defaultConferenceSeq <= 0))) {
            throw new IllegalStateException("Single conference mode requires app.public-site.default-conference-seq.");
        }
    }
}
