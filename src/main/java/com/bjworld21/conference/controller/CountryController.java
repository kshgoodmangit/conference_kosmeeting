package com.bjworld21.conference.controller;

import com.bjworld21.conference.dto.CountryPageResponse;
import com.bjworld21.conference.dto.CountryResponse;
import com.bjworld21.conference.service.CountryService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class CountryController {
    private final CountryService countryService;

    public CountryController(CountryService countryService) {
        this.countryService = countryService;
    }

    @GetMapping("/admin/countries")
    public ResponseEntity<CountryPageResponse> list(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(defaultValue = "") String keyword
    ) {
        return ResponseEntity.ok(countryService.findPage(page, size, keyword));
    }

    @GetMapping("/countries/used")
    @com.bjworld21.conference.config.IpAccessExempt
    public ResponseEntity<List<CountryResponse>> listUsed() {
        return ResponseEntity.ok(countryService.findUsed());
    }

    @PutMapping("/admin/countries/{seq}/is-used")
    public ResponseEntity<?> updateIsUsed(
            @PathVariable Long seq,
            @RequestParam String isUsed
    ) {
        try {
            return ResponseEntity.ok(countryService.updateIsUsed(seq, isUsed));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("국가 사용 여부 수정 중 오류가 발생했습니다.");
        }
    }
}
