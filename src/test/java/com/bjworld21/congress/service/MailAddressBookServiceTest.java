package com.bjworld21.congress.service;

import com.bjworld21.congress.entity.MailAddressBook;
import com.bjworld21.congress.repository.MailAddressBookRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MailAddressBookServiceTest {
    private MailAddressBookRepository repository;
    private MailAddressBookService service;

    @BeforeEach
    void setUp() {
        repository = mock(MailAddressBookRepository.class);
        service = new MailAddressBookService(repository, PersonalDataTestSupport.properties());
        when(repository.findBySeq(1L)).thenReturn(MailAddressBook.builder().seq(1L).build());
    }

    @Test
    void deletesUnusedAddressBook() {
        when(repository.delete(1L)).thenReturn(1);

        service.delete(1L);

        verify(repository).countCampaignSources(1L);
        verify(repository).delete(1L);
    }

    @Test
    void rejectsAddressBookUsedByCampaign() {
        when(repository.countCampaignSources(1L)).thenReturn(1);

        assertThatThrownBy(() -> service.delete(1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("캠페인 수신자로 사용 중인 주소록은 삭제할 수 없습니다.");

        verify(repository, never()).delete(1L);
    }

    @Test
    void deletesContactOnlyFromOwningAddressBook() {
        when(repository.deleteContact(1L, 20L)).thenReturn(1);

        service.deleteContact(1L, 20L);

        verify(repository).deleteContact(1L, 20L);
    }
}
