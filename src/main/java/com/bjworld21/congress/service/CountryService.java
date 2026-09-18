package com.bjworld21.congress.service;

import com.bjworld21.congress.dto.CountryPageResponse;
import com.bjworld21.congress.dto.CountryResponse;
import com.bjworld21.congress.entity.Country;
import com.bjworld21.congress.repository.CountryRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

@Service
public class CountryService {
    private final CountryRepository countryRepository;

    public CountryService(CountryRepository countryRepository) {
        this.countryRepository = countryRepository;
    }

    public CountryPageResponse findPage(int page, int size, String keyword) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 100);
        String normalizedKeyword = keyword == null ? "" : keyword.trim();
        long totalCount = countryRepository.countByKeyword(normalizedKeyword);
        int totalPages = Math.max(1, (int) Math.ceil((double) totalCount / safeSize));
        int adjustedPage = Math.min(safePage, totalPages);
        int offset = (adjustedPage - 1) * safeSize;

        return CountryPageResponse.builder()
                .items(countryRepository.findPage(normalizedKeyword, safeSize, offset).stream()
                        .map(this::toResponse)
                        .toList())
                .page(adjustedPage)
                .size(safeSize)
                .totalCount(totalCount)
                .usedCount(countryRepository.countByKeywordAndIsUsed(normalizedKeyword, "Y"))
                .unusedCount(countryRepository.countByKeywordAndIsUsed(normalizedKeyword, "N"))
                .totalPages(totalPages)
                .build();
    }

    public List<CountryResponse> findUsed() {
        return countryRepository.findUsed().stream()
                .map(this::toResponse)
                .toList();
    }

    public CountryResponse updateIsUsed(Long seq, String isUsed) {
        Country country = countryRepository.findBySeq(seq);
        if (country == null) {
            throw new IllegalArgumentException("국가 정보를 찾을 수 없습니다.");
        }

        String normalizedIsUsed = isUsed == null ? "" : isUsed.trim().toUpperCase(Locale.ROOT);
        if (!"Y".equals(normalizedIsUsed) && !"N".equals(normalizedIsUsed)) {
            throw new IllegalArgumentException("사용 여부는 Y 또는 N만 입력할 수 있습니다.");
        }

        countryRepository.updateIsUsed(seq, normalizedIsUsed);
        return toResponse(countryRepository.findBySeq(seq));
    }

    private CountryResponse toResponse(Country country) {
        return CountryResponse.builder()
                .seq(country.getSeq())
                .isoAlpha2(country.getIsoAlpha2())
                .isoAlpha3(country.getIsoAlpha3())
                .isoNumeric(country.getIsoNumeric())
                .countryName(country.getCountryName())
                .countryNameKo(country.getCountryNameKo())
                .dialCode(country.getDialCode())
                .isUsed(country.getIsUsed())
                .build();
    }
}
