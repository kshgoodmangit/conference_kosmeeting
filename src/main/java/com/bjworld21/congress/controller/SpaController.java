package com.bjworld21.congress.controller;

import com.bjworld21.congress.config.IpAccessExempt;
import org.springframework.stereotype.Controller;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class SpaController {

    @GetMapping(
            value = {
                    "/admin",
                    "/admin/",
                    "/admin/{*path}"
            },
            produces = MediaType.TEXT_HTML_VALUE
    )
    @IpAccessExempt
    public String forwardSpaRoute() {
        return "forward:/index.html";
    }
}
