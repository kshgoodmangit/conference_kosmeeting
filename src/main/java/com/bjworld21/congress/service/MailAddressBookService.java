package com.bjworld21.congress.service;

import com.bjworld21.congress.config.PersonalDataProperties;
import com.bjworld21.congress.dto.MailAddressBookRequest;
import com.bjworld21.congress.dto.MailContactRequest;
import com.bjworld21.congress.entity.MailAddressBook;
import com.bjworld21.congress.entity.MailContact;
import com.bjworld21.congress.repository.MailAddressBookRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
public class MailAddressBookService {
    private final MailAddressBookRepository repository;
    private final PersonalDataProperties personalDataProperties;

    public MailAddressBookService(MailAddressBookRepository repository,
                                  PersonalDataProperties personalDataProperties) {
        this.repository = repository;
        this.personalDataProperties = personalDataProperties;
    }

    public List<MailAddressBook> findAll(String keyword) {
        return repository.findAll(trimToEmpty(keyword));
    }

    public MailAddressBook create(MailAddressBookRequest request) {
        MailAddressBook addressBook = MailAddressBook.builder()
                .addressBookName(request.getAddressBookName().trim())
                .description(trimToNull(request.getDescription()))
                .build();
        repository.insert(addressBook);
        return requireAddressBook(addressBook.getSeq());
    }

    public MailAddressBook update(Long seq, MailAddressBookRequest request) {
        MailAddressBook addressBook = requireAddressBook(seq);
        addressBook.setAddressBookName(request.getAddressBookName().trim());
        addressBook.setDescription(trimToNull(request.getDescription()));
        repository.update(addressBook);
        return requireAddressBook(seq);
    }

    @Transactional
    public void delete(Long seq) {
        requireAddressBook(seq);
        if (repository.countCampaignSources(seq) > 0) {
            throw new IllegalStateException("캠페인 수신자로 사용 중인 주소록은 삭제할 수 없습니다.");
        }
        try {
            if (repository.delete(seq) == 0) {
                throw new IllegalArgumentException("존재하지 않는 주소록입니다.");
            }
        } catch (DataIntegrityViolationException exception) {
            throw new IllegalStateException("캠페인 수신자로 사용 중인 주소록은 삭제할 수 없습니다.", exception);
        }
    }

    public List<MailContact> findContacts(Long addressBookSeq, String keyword) {
        requireAddressBook(addressBookSeq);
        return repository.findContacts(addressBookSeq, trimToEmpty(keyword), dbEncString());
    }

    @Transactional
    public MailContact saveContact(Long addressBookSeq, MailContactRequest request) {
        requireAddressBook(addressBookSeq);
        String email = request.getEmail().trim();
        MailContact contact = MailContact.builder()
                .addressBookSeq(addressBookSeq)
                .email(email)
                .normalizedEmail(normalizeEmail(email))
                .fullName(trimToNull(request.getFullName()))
                .affiliation(trimToNull(request.getAffiliation()))
                .country(trimToNull(request.getCountry()))
                .phoneNumber(trimToNull(request.getPhoneNumber()))
                .memo(trimToNull(request.getMemo()))
                .build();
        repository.upsertContact(contact, dbEncString());
        return repository.findContactByNormalizedEmail(addressBookSeq, contact.getNormalizedEmail(), dbEncString());
    }

    public void deleteContact(Long addressBookSeq, Long contactSeq) {
        requireAddressBook(addressBookSeq);
        if (repository.deleteContact(addressBookSeq, contactSeq) == 0) {
            throw new IllegalArgumentException("주소록에 존재하지 않는 연락처입니다.");
        }
    }

    public MailAddressBook requireAddressBook(Long seq) {
        MailAddressBook addressBook = repository.findBySeq(seq);
        if (addressBook == null) {
            throw new IllegalArgumentException("존재하지 않는 주소록입니다.");
        }
        return addressBook;
    }

    public static String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }
    private String dbEncString() {
        return personalDataProperties.requireDbEncString();
    }
}
