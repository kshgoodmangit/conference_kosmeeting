(() => {
    /*
     * Public-site behavior bundle. Templates expose stable IDs and data-* hooks consumed here;
     * rename a hook only when updating both the template and this file. Mutating requests carry
     * the Thymeleaf CSRF token, and a 401 from member APIs redirects to the public login page.
     */

    // 메인 비주얼/연사/스폰서/갤러리 슬라이더 및 프로그램 날짜 탭 처리
    if (window.jQuery?.fn.slick) {
        const heroSlider = $('.main-hero-background');
        const heroNavigation = $('.main-hero-navigation');

        heroSlider.on('init reInit afterChange', (event, slick, currentSlide) => {
            const current = (currentSlide || 0) + 1;

            heroNavigation.find('button').each(function (index) {
                const active = index === current - 1;
                $(this).toggleClass('is-active', active).attr('aria-current', String(active));
            });
            heroNavigation.find('strong').text(current);
        });

        heroNavigation.on('click', 'button', function () {
            heroSlider.slick('slickGoTo', Number($(this).data('slide')));
        });

        heroSlider.slick({
            arrows: false,
            autoplay: true,
            autoplaySpeed: 5000,
            fade: true,
            speed: 800,
            pauseOnHover: false,
            pauseOnFocus: false
        });

        $('.main-speaker-grid').slick({
            slidesToShow: 4,
            slidesToScroll: 1,
            responsive: [
                {breakpoint: 992, settings: {slidesToShow: 2}},
                {breakpoint: 576, settings: {slidesToShow: 1}}
            ]
        });

        $('.main-sponsors-grid').slick({
            infinite: true,
            slidesToShow: 6,
            slidesToScroll: 1,
            autoplay: true,
            autoplaySpeed: 5000,
            arrows: false,
            responsive: [
                {breakpoint: 992, settings: {slidesToShow: 4}},
                {breakpoint: 576, settings: {slidesToShow: 2}}
            ]
        });

        $('.slick-gallery-main').slick({
            arrows: false,
            fade: true,
            asNavFor: '.slick-gallery-nav'
        });

        $('.slick-gallery-nav').slick({
            slidesToShow: 4,
            slidesToScroll: 1,
            asNavFor: '.slick-gallery-main',
            focusOnSelect: true,
            responsive: [
                {breakpoint: 768, settings: {slidesToShow: 2}},
                {breakpoint: 576, settings: {slidesToShow: 1}}
            ]
        });
    }

    document.querySelectorAll('.scientific-tabs').forEach((tabs) => {
        tabs.addEventListener('click', (event) => {
            const tab = event.target instanceof Element ? event.target.closest('.scientific-tab') : null;
            if (!(tab instanceof HTMLButtonElement)) {
                return;
            }

            const dayId = tab.dataset.day;
            const section = tab.closest('.scientific-section, .main-program');

            if (!dayId || !(section instanceof HTMLElement)) {
                return;
            }

            section.querySelectorAll('.scientific-tab').forEach((item) => {
                item.classList.toggle('active', item === tab);
                item.setAttribute('aria-selected', String(item === tab));
            });

            section.querySelectorAll('.scientific-day-panel').forEach((panel) => {
                panel.classList.toggle('active', panel.id === dayId);
            });
        });
    });

    // 지도 영역이 존재하는 페이지에서만 Google Map iframe을 동적으로 생성
    document.addEventListener("DOMContentLoaded", function () {
        const mapWrap = document.querySelector(".map-grid-iframe");

        if (!mapWrap) return;

        const mapUrl = "https://www.google.com/maps/embed?pb=!1m5!3m3!1m2!1s0x357ca157ddbed32f%3A0x29432bdf4b90af3d!2s22%20Teheran-ro%207-gil%2C%20Gangnam%20District%2C%20Seoul!5e0!3m2!1sen!2skr!4v1788941974804!5m2!1sen!2skr";

        if (mapUrl) {
            const iframe = document.createElement("iframe");

            iframe.src = mapUrl;
            iframe.width = "100%";
            iframe.height = "450";
            iframe.style.border = "0";
            iframe.loading = "lazy";
            iframe.allowFullscreen = true;

            mapWrap.appendChild(iframe);
        }
    });

    document.addEventListener("DOMContentLoaded", function () {
        const mapWrap = document.querySelector(".kstc-map-iframe");

        if (!mapWrap) return;

        const mapUrl = "https://www.google.com/maps/embed?pb=!1m5!3m3!1m2!1s0x357ca157de00cbb3%3A0xe5266ee55f1d179e!2sKorea%20Institute%20of%20Science%20and%20Technology%20Center!5e0!3m2!1sen!2skr!4v1789004331064!5m2!1sen!2skr";

        if (mapUrl) {
            const iframe = document.createElement("iframe");

            iframe.src = mapUrl;
            iframe.width = "100%";
            iframe.height = "372";
            iframe.style.border = "0";
            iframe.loading = "lazy";
            iframe.allowFullscreen = true;

            mapWrap.appendChild(iframe);
        }
    });

    document.addEventListener("DOMContentLoaded", function () {

        // href="#" 링크 기본 동작 방지
        document.querySelectorAll('a[href="#"]').forEach(function (link) {
            link.addEventListener("click", function (e) {
                e.preventDefault();
            });
        });

        // 모바일 메뉴
        document.querySelectorAll(".mobile-menu-list > li > a").forEach(function (link) {
            link.addEventListener("click", function (e) {
                if (this.nextElementSibling?.matches('.header-sub-menu')) e.preventDefault();
            });
        });

    });

    // 회원가입 폼 처리: 유효성 검사 → 회원가입 API 호출 → 성공 페이지 이동
    // 회원가입 폼 이벤트와 유효성 검사를 설정
    const validateMemberPasswordLength = (input, optional = false) => {
        if (!(input instanceof HTMLInputElement)) return true;
        const length = input.value.trim().length;
        input.setCustomValidity((optional && length === 0) || (length >= 8 && length <= 16)
            ? '' : window.PublicSite.t('Password must be 8-16 characters.'));
        return input.reportValidity();
    };

    const setupMemberRegistration = (form) => {
        const passwordInput = form.querySelector('[name="password"]');
        const passwordConfirmInput = form.querySelector('[name="passwordConfirm"]');
        const submitButton = form.querySelector('button[type="submit"]');

        passwordInput?.addEventListener('input', () => passwordInput.setCustomValidity(''));

        passwordConfirmInput?.addEventListener('input', () => {
            if (passwordConfirmInput instanceof HTMLInputElement) passwordConfirmInput.setCustomValidity('');
        });

        form.addEventListener('submit', async (event) => {
            event.preventDefault();
            const verification = form.querySelector('[data-email-verification]');
            const signupEmail = form.querySelector('[data-verification-email]');
            if (verification && signupEmail instanceof HTMLInputElement
                    && (verification.dataset.verifiedEmail !== signupEmail.value.trim().toLowerCase()
                        || Number(verification.dataset.verifiedUntil || 0) <= Date.now())) {
                const message = verification.querySelector('[data-email-verification-status]');
                if (message instanceof HTMLElement) {
                    message.textContent = window.PublicSite.t('Please verify your email before signing up.');
                    message.hidden = false;
                    message.focus();
                }
                return;
            }
            if (!validateMemberPasswordLength(passwordInput)) return;

            if (passwordConfirmInput instanceof HTMLInputElement) {
                passwordConfirmInput.setCustomValidity('');
            }
            if (passwordInput instanceof HTMLInputElement && passwordConfirmInput instanceof HTMLInputElement
                && passwordInput.value.trim() !== passwordConfirmInput.value.trim()) {
                passwordConfirmInput.setCustomValidity(window.PublicSite.t('Passwords do not match.'));
                window.alert(window.PublicSite.t('Passwords do not match.'));
                return;
            }

            if (!form.checkValidity()) {
                window.alert(window.PublicSite.t('Please complete all required fields correctly.'));
                return;
            }

            const originalButtonText = submitButton?.textContent;
            if (submitButton instanceof HTMLButtonElement) {
                submitButton.disabled = true;
                submitButton.textContent = window.PublicSite.t('Signing Up...');
            }

            try {
                const response = await fetch(form.action, {
                    method: 'POST',
                    body: new FormData(form),
                    headers: {'Accept': 'application/json'}
                });
                if (!response.ok) throw new Error(await window.PublicSite.responseMessage(response) || window.PublicSite.t('Registration failed.'));

                const successUrl = form.dataset.successUrl || window.PublicSite.pageUrl('/login');
                window.alert(window.PublicSite.t('Registration complete. Please log in.'));
                window.location.assign(successUrl);
            } catch (error) {
                window.alert(error instanceof Error ? error.message : window.PublicSite.t('Registration failed. Please try again.'));
                if (submitButton instanceof HTMLButtonElement) {
                    submitButton.disabled = false;
                    submitButton.textContent = originalButtonText || window.PublicSite.t('Sign Up');
                }
            }
        });
    };

    document.querySelectorAll('#international-join-form, #domestic-join-form').forEach((form) => {
        if (form instanceof HTMLFormElement) setupMemberRegistration(form);
    });

    // Email code issuance uses the configured server-side Gmail sender.
    document.querySelectorAll('[data-email-verification]').forEach((section) => {
        const email = section.querySelector('[data-verification-email]');
        const code = section.querySelector('[data-email-code]');
        const send = section.querySelector('[data-send-email-code]');
        const verify = section.querySelector('[data-verify-email-code]');
        const status = section.querySelector('[data-email-verification-status]');
        const memberLinks = section.querySelector('[data-email-existing-member-links]');
        const signup = email?.form?.querySelector('button[type="submit"]');
        if (!(email instanceof HTMLInputElement) || !(code instanceof HTMLInputElement)
                || !(send instanceof HTMLButtonElement) || !(verify instanceof HTMLButtonElement)
                || !(status instanceof HTMLElement)) return;

        let sending = false;
        let verifying = false;
        let existingMember = false;
        let generation = 0;
        let expiresAt = 0;
        let resendAt = 0;
        let timer = null;
        const clearStatus = () => {
            status.textContent = '';
            status.hidden = true;
        };
        const resetVerification = () => {
            generation++;
            existingMember = false;
            section.dataset.verifiedEmail = '';
            section.dataset.verifiedUntil = '';
            if (signup instanceof HTMLButtonElement) signup.disabled = true;
            if (memberLinks instanceof HTMLElement) memberLinks.hidden = true;
            expiresAt = 0;
            code.value = '';
            code.disabled = true;
            verify.disabled = true;
            verify.textContent = window.PublicSite.t('Verify');
            clearStatus();
        };
        const validateEmail = () => {
            email.value = email.value.trim();
            return email.reportValidity();
        };

        const refreshButtons = () => {
            const remaining = Math.max(0, Math.ceil((resendAt - Date.now()) / 1000));
            const verified = Boolean(section.dataset.verifiedEmail);
            if (verified && Number(section.dataset.verifiedUntil) <= Date.now()) {
                resetVerification();
                status.textContent = window.PublicSite.t('Email verification has expired. Please request a new code.');
                status.hidden = false;
            }
            if (expiresAt && Date.now() >= expiresAt) {
                resetVerification();
                status.textContent = window.PublicSite.t('The code has expired. Please request a new code.');
                status.hidden = false;
            }
            if (!expiresAt && !section.dataset.verifiedEmail && remaining === 0 && timer !== null) {
                clearInterval(timer);
                timer = null;
            }
            send.disabled = sending || verifying || Boolean(section.dataset.verifiedEmail) || existingMember || remaining > 0;
            send.textContent = sending ? window.PublicSite.t('Sending...') : section.dataset.verifiedEmail || existingMember ? window.PublicSite.t('Code sent') : remaining > 0 ? window.PublicSite.t('Resend in {seconds}s', {seconds: remaining}) : expiresAt ? window.PublicSite.t('Resend code') : window.PublicSite.t('Send code');
        };
        send.addEventListener('click', async () => {
            if (sending || verifying || section.dataset.verifiedEmail || existingMember || Date.now() < resendAt || !validateEmail()) return;
            resetVerification();
            const requestGeneration = generation;
            const requestedEmail = email.value;
            sending = true;
            email.readOnly = true;
            refreshButtons();
            try {
                const csrf = email.form?.querySelector('[name="_csrf"]');
                const response = await fetch(window.PublicSite.apiUrl('/api/public/members/email-verification/send'), {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/json',
                        'Accept': 'application/json',
                        ...(csrf instanceof HTMLInputElement ? {'X-CSRF-TOKEN': csrf.value} : {})
                    },
                    body: JSON.stringify({email: requestedEmail})
                });
                if (!response.ok) {
                    if (response.status === 429) resendAt = Date.now() + 60000;
                    const message = await window.PublicSite.responseMessage(response);
                    throw new Error(response.status === 403
                        ? window.PublicSite.t('Your session has expired. Please reload this page and try again.')
                        : message || window.PublicSite.t('The verification code could not be sent. Please try again later.'));
                }
                const result = await response.json();
                resendAt = Date.now() + result.resendAfterSeconds * 1000;
                if (requestGeneration !== generation || email.value !== requestedEmail) return;
                expiresAt = Date.now() + result.expiresInSeconds * 1000;
                code.disabled = false;
                status.textContent = `${result.message} ${window.PublicSite.t('The code is valid for {minutes} minutes.', {minutes: Math.ceil(result.expiresInSeconds / 60)})}`;
                status.hidden = false;
                code.focus();
            } catch (error) {
                if (requestGeneration === generation) {
                    status.textContent = error instanceof Error ? error.message : window.PublicSite.t('The verification code could not be sent.');
                    status.hidden = false;
                }
            } finally {
                sending = false;
                email.readOnly = false;
                refreshButtons();
                if (timer === null && (expiresAt || Date.now() < resendAt)) timer = setInterval(refreshButtons, 1000);
            }
        });
        verify.addEventListener('click', async () => {
            refreshButtons();
            if (sending || verifying || code.disabled || !validateEmail() || !/^\d{6}$/.test(code.value)) return;
            const requestGeneration = generation;
            const requestedEmail = email.value;
            verifying = true;
            email.readOnly = true;
            code.readOnly = true;
            verify.disabled = true;
            verify.textContent = window.PublicSite.t('Verifying...');
            refreshButtons();
            try {
                const csrf = email.form?.querySelector('[name="_csrf"]');
                const response = await fetch(window.PublicSite.apiUrl('/api/public/members/email-verification/verify'), {
                    method: 'POST',
                    headers: {'Content-Type': 'application/json', 'Accept': 'application/json',
                        ...(csrf instanceof HTMLInputElement ? {'X-CSRF-TOKEN': csrf.value} : {})},
                    body: JSON.stringify({email: requestedEmail, code: code.value})
                });
                if (!response.ok) {
                    const message = await window.PublicSite.responseMessage(response);
                    if (requestGeneration !== generation) return;
                    if (response.status === 410 || response.status === 429 || response.status === 403) resetVerification();
                    status.textContent = response.status === 403
                        ? window.PublicSite.t('Your session has expired. Please reload this page and try again.')
                        : message || window.PublicSite.t('The code could not be verified. Please try again.');
                    status.hidden = false;
                    return;
                }
                const result = await response.json();
                if (requestGeneration !== generation || email.value !== requestedEmail) return;
                expiresAt = 0;
                existingMember = result.existingMember;
                code.disabled = true;
                code.value = '';
                verify.textContent = window.PublicSite.t('Verified');
                if (memberLinks instanceof HTMLElement) memberLinks.hidden = !existingMember;
                if (!existingMember) {
                    section.dataset.verifiedEmail = requestedEmail.trim().toLowerCase();
                    section.dataset.verifiedUntil = String(Date.now() + result.expiresInSeconds * 1000);
                    if (signup instanceof HTMLButtonElement) signup.disabled = false;
                }
                status.textContent = result.message;
                status.hidden = false;
            } catch (error) {
                if (requestGeneration === generation) {
                    status.textContent = window.PublicSite.t('The code could not be verified. Please try again.');
                    status.hidden = false;
                }
            } finally {
                verifying = false;
                email.readOnly = false;
                code.readOnly = false;
                verify.disabled = code.disabled || !/^\d{6}$/.test(code.value);
                if (!section.dataset.verifiedEmail && !existingMember) verify.textContent = window.PublicSite.t('Verify');
                refreshButtons();
            }
        });
        const onEmailChanged = () => { resetVerification(); refreshButtons(); };
        email.addEventListener('input', onEmailChanged);
        email.addEventListener('change', onEmailChanged);
        email.form?.addEventListener('reset', onEmailChanged);
        code.addEventListener('input', () => {
            code.value = code.value.replace(/\D/g, '').slice(0, 6);
            verify.disabled = verifying || code.disabled || !/^\d{6}$/.test(code.value);
            clearStatus();
        });
        code.addEventListener('keydown', (event) => {
            if (event.key === 'Enter') {
                event.preventDefault();
                if (!verify.disabled) verify.click();
            }
        });
        // Signup is enabled only after server verification; the registration API independently enforces it.
        resetVerification();
        send.disabled = false;
    });

    // 관리자에서 사용하는 국가 목록을 조회하여 국가 선택박스 구성
    // 국가 목록을 조회하고 국가 선택 및 전화 국가코드를 연동
    const setupCountrySelect = (countryInput) => {
        if (!(countryInput instanceof HTMLSelectElement)) return;

        const form = countryInput.form;
        const countryCodeTarget = countryInput.dataset.countryCodeTarget;
        const mobileCountryCodeInput = countryCodeTarget ? form?.querySelector(countryCodeTarget) : null;
        const countryCodeDisplay = countryInput.dataset.countryCodeDisplay
            ? form?.querySelector(countryInput.dataset.countryCodeDisplay) : null;
        const countryDialCodes = new Map();

        fetch('/api/countries/used')
            .then(async (response) => {
                if (!response.ok) throw new Error(await window.PublicSite.responseMessage(response) || window.PublicSite.t('Country list could not be loaded.'));
                return response.json();
            })
            .then((countries) => {
                countries.forEach((country) => {
                    const option = new Option(country.countryName, country.countryName);
                    option.defaultSelected = country.countryName === countryInput.dataset.selectedCountry;
                    option.selected = option.defaultSelected;
                    countryInput.append(option);
                    countryDialCodes.set(country.countryName, country.dialCode || '');
                });
                const savedCountry = countryInput.dataset.selectedCountry;
                if (savedCountry && !countryDialCodes.has(savedCountry)) {
                    countryInput.append(new Option(savedCountry, savedCountry, true, true));
                }

                updateMobileCountryCode();

                if (window.jQuery?.fn.select2) {
                    window.jQuery(countryInput).select2({
                        placeholder: countryInput.options[0]?.text || window.PublicSite.t('Select a country.'),
                        minimumResultsForSearch: 0,
                        width: '100%'
                    });
                }
            })
            .catch((error) => window.alert(error instanceof Error ? error.message : window.PublicSite.t('Country list could not be loaded.')));

        const updateMobileCountryCode = () => {
            if (countryInput instanceof HTMLSelectElement && mobileCountryCodeInput instanceof HTMLInputElement) {
                mobileCountryCodeInput.value = countryDialCodes.get(countryInput.value) || mobileCountryCodeInput.value;
                if (countryCodeDisplay) countryCodeDisplay.textContent = mobileCountryCodeInput.value || '-';
            }
        };

        if (window.jQuery) {
            window.jQuery(countryInput).on('change', updateMobileCountryCode);
        } else {
            countryInput?.addEventListener('change', updateMobileCountryCode);
        }

    };

    document.querySelectorAll('[data-country-select]').forEach(setupCountrySelect);

    // 마이페이지 회원정보 수정 처리 (현재 API 정책상 이메일은 수정하지 않음)
    const memberProfileForm = document.querySelector('#mypage-profile-form');

    if (memberProfileForm instanceof HTMLFormElement) {
        const submitButton = memberProfileForm.querySelector('button[type="submit"]');
        const profileStatus = memberProfileForm.querySelector('[data-profile-status]');
        let savingProfile = false;
        const showProfileStatus = (message, error = false) => {
            profileStatus.textContent = message;
            profileStatus.hidden = false;
            profileStatus.classList.toggle('is-error', error);
            profileStatus.focus();
        };

        memberProfileForm.addEventListener('submit', async (event) => {
            event.preventDefault();
            if (savingProfile || !memberProfileForm.reportValidity()) return;
            savingProfile = true;
            profileStatus.hidden = true;
            memberProfileForm.setAttribute('aria-busy', 'true');

            const originalButtonHtml = submitButton?.innerHTML;
            if (submitButton instanceof HTMLButtonElement) {
                submitButton.disabled = true;
                submitButton.textContent = window.PublicSite.t('Saving...');
            }

            try {
                const response = await fetch(memberProfileForm.action, {
                    method: 'POST',
                    credentials: 'same-origin',
                    body: new FormData(memberProfileForm),
                    headers: {'Accept': 'application/json'}
                });
                if (response.status === 401) {
                    window.location.assign(window.PublicSite.pageUrl('/login'));
                    return;
                }
                if (response.status === 403 && response.headers.get('X-CSRF-ERROR') === 'true') {
                    throw new Error(window.PublicSite.t('Your session has expired. Please reload this page and try again.'));
                }
                if (!response.ok) throw new Error(await window.PublicSite.responseMessage(response) || window.PublicSite.t('Profile update failed.'));

                showProfileStatus(window.PublicSite.t('Profile saved.'));
            } catch (error) {
                showProfileStatus(error instanceof Error ? error.message : window.PublicSite.t('Profile update failed. Please try again.'), true);
            } finally {
                savingProfile = false;
                memberProfileForm.removeAttribute('aria-busy');
                if (submitButton instanceof HTMLButtonElement) {
                    submitButton.disabled = false;
                    submitButton.innerHTML = originalButtonHtml || window.PublicSite.t('Save');
                }
            }
        });
    }

    // 로그인 처리 및 회원용 API에서 사용할 서버 세션 생성
    const memberLoginForm = document.querySelector('#member-login-form');

    if (memberLoginForm instanceof HTMLFormElement) {
        const submitButton = memberLoginForm.querySelector('button[type="submit"]');
        const passwordNotice = memberLoginForm.querySelector('[data-password-login-notice]');
        const loginUrl = new URL(window.location.href);
        const passwordStatus = loginUrl.searchParams.get('password');
        if (passwordNotice && (passwordStatus === 'changed' || passwordStatus === 'session-expired')) {
            passwordNotice.textContent = passwordStatus === 'changed'
                ? window.PublicSite.t('Your password has been changed. Please sign in again.')
                : window.PublicSite.t('Your session has expired. Please sign in again.');
            passwordNotice.hidden = false;
            loginUrl.searchParams.delete('password');
            window.history.replaceState(null, '', loginUrl.pathname + loginUrl.search + loginUrl.hash);
        }

        memberLoginForm.addEventListener('submit', async (event) => {
            event.preventDefault();
            if (!memberLoginForm.checkValidity()) {
                window.alert(window.PublicSite.t('Please enter your email and password.'));
                return;
            }

            const originalButtonText = submitButton?.textContent;
            if (submitButton instanceof HTMLButtonElement) {
                submitButton.disabled = true;
                submitButton.textContent = window.PublicSite.t('LOGGING IN...');
            }

            try {
                const response = await fetch(memberLoginForm.action, {
                    method: 'POST',
                    body: new FormData(memberLoginForm),
                    headers: {'Accept': 'application/json'}
                });
                if (!response.ok) throw new Error(await window.PublicSite.responseMessage(response) || window.PublicSite.t('Login failed.'));

                window.location.assign(memberLoginForm.dataset.successUrl || window.PublicSite.pageUrl('/mypage'));
            } catch (error) {
                window.alert(error instanceof Error ? error.message : window.PublicSite.t('Login failed. Please try again.'));
                if (submitButton instanceof HTMLButtonElement) {
                    submitButton.disabled = false;
                    submitButton.textContent = originalButtonText || window.PublicSite.t('LOGIN');
                }
            }
        });
    }

    // 초록 등록/수정 처리: 메타데이터·국가목록 조회 후 seq가 있으면 기존 초록 데이터 로드
    const publicAbstractForm = document.querySelector('#public-abstract-form');

    if (publicAbstractForm instanceof HTMLFormElement) {
        const institutionList = publicAbstractForm.querySelector('[data-institution-list]');
        const authorList = publicAbstractForm.querySelector('[data-author-list]');
        const institutionTemplate = document.querySelector('#abstract-institution-template');
        const authorTemplate = document.querySelector('#abstract-author-template');
        const presentationType = publicAbstractForm.querySelector('[name="presentationTypeCode"]');
        const category = publicAbstractForm.querySelector('[name="categoryCode"]');
        const aiTools = publicAbstractForm.querySelector('[data-ai-tools]');
        const aiScopes = publicAbstractForm.querySelector('[data-ai-scopes]');
        const wordCountOutput = publicAbstractForm.querySelector('[data-word-count]');
        const formStatus = publicAbstractForm.querySelector('[data-abstract-status]');
        const editingSeq = new URLSearchParams(window.location.search).get('seq');
        let countries = [];

        // select2가 사용 가능한 경우 기본 select를 select2 UI로 적용/갱신
        const enhanceSelect = (select) => {
            if (!(select instanceof HTMLSelectElement) || !window.jQuery?.fn.select2) return;
            const target = window.jQuery(select);
            if (target.hasClass('select2-hidden-accessible')) {
                target.trigger('change.select2');
                return;
            }
            target.select2({
                placeholder: select.options[0]?.text || 'Select',
                minimumResultsForSearch: select.matches('[name="institutionCountry"], [name="authorCountry"]') ? 0 : Infinity,
                width: '100%'
            });
        };

        // 폼 처리 상태 또는 오류 메시지를 화면에 표시
        const setStatus = (message, error = false) => {
            if (!(formStatus instanceof HTMLElement)) return;
            formStatus.textContent = message;
            formStatus.hidden = !message;
            formStatus.classList.toggle('is-error', error);
        };

        // API에서 받은 코드 목록을 select option으로 구성
        const addOptions = (select, items, placeholder) => {
            if (!(select instanceof HTMLSelectElement)) return;
            const selected = select.value;
            select.replaceChildren(new Option(placeholder, ''));
            items.forEach((item) => select.append(new Option(item.name, String(item.code))));
            select.value = selected;
            enhanceSelect(select);
        };

        // 조회된 국가 목록을 국가 선택 select에 추가
        const addCountryOptions = (select) => {
            if (!(select instanceof HTMLSelectElement)) return;
            const selected = select.value;
            select.replaceChildren(new Option(window.PublicSite.t('Select a country'), ''));
            countries.forEach((country) => select.append(new Option(country.countryName, country.countryName)));
            select.value = selected;
            enhanceSelect(select);
        };

        // 소속기관 번호를 다시 매기고 저자별 소속기관 선택 목록을 동기화
        const refreshInstitutionNumbers = () => {
            const rows = Array.from(institutionList?.children || []);
            rows.forEach((row, index) => {
                const number = row.querySelector('[data-institution-number]');
                if (number) number.textContent = String(index + 1);
                const remove = row.querySelector('[data-remove-institution]');
                if (remove instanceof HTMLButtonElement) remove.disabled = rows.length === 1;
            });

            authorList?.querySelectorAll('[name="authorInstitutionNo"]').forEach((select) => {
                if (!(select instanceof HTMLSelectElement)) return;
                const selected = select.value;
                select.replaceChildren(...rows.map((row, index) => {
                    const name = row.querySelector('[name="institutionName"]')?.value.trim();
                    const department = row.querySelector('[name="institutionDepartment"]')?.value.trim();
                    return new Option([name || window.PublicSite.t('Institution {number}', {number: index + 1}), department].filter(Boolean).join(' · '), String(index + 1));
                }));
                select.value = Array.from(select.options).some((option) => option.value === selected) ? selected : '1';
                enhanceSelect(select);
            });
        };

        // 저자의 국가 선택값에 맞춰 사무실/휴대전화 국가코드를 자동 설정
        const updateAuthorCountryCodes = (countrySelect) => {
            if (!(countrySelect instanceof HTMLSelectElement)) return;
            const card = countrySelect.closest('.abstract-author-card');
            const dialCode = countries.find((country) => country.countryName === countrySelect.value)?.dialCode || '';
            ['officeCountryCode', 'mobileCountryCode'].forEach((name) => {
                const input = card?.querySelector(`[name="${name}"]`);
                if (input instanceof HTMLInputElement) input.value = dialCode;
            });
        };

        // 저자 국가 변경 이벤트에 전화 국가코드 갱신 기능 연결
        const bindAuthorCountryCodes = (countrySelect) => {
            if (!(countrySelect instanceof HTMLSelectElement)) return;
            if (window.jQuery) {
                window.jQuery(countrySelect).on('change.abstractCountryCodes', () => updateAuthorCountryCodes(countrySelect));
            } else {
                countrySelect.addEventListener('change', () => updateAuthorCountryCodes(countrySelect));
            }
        };

        // 저자 순서·역할·삭제/이동 버튼 상태를 현재 목록 기준으로 갱신
        const refreshAuthors = () => {
            const cards = Array.from(authorList?.children || []);
            cards.forEach((card, index) => {
                const presenting = card.querySelector('[name="isPresentingAuthor"]');
                const order = card.querySelector('[data-author-order]');
                const role = card.querySelector('[data-author-role]');
                if (order) order.textContent = window.PublicSite.t('Order {number}', {number: index + 1});
                if (role) role.textContent = presenting instanceof HTMLInputElement && presenting.checked ? window.PublicSite.t('Presenting Author') : window.PublicSite.t('Author');
                card.querySelectorAll('[data-remove-author]').forEach((button) => {
                    if (button instanceof HTMLButtonElement) button.disabled = cards.length === 1;
                });
                const up = card.querySelector('[data-move-author="up"]');
                const down = card.querySelector('[data-move-author="down"]');
                if (up instanceof HTMLButtonElement) up.disabled = index === 0;
                if (down instanceof HTMLButtonElement) down.disabled = index === cards.length - 1;
            });
        };

        // 소속기관 입력 템플릿을 복제하여 새 기관 행 추가
        const addInstitution = () => {
            if (!(institutionList instanceof HTMLElement) || !(institutionTemplate instanceof HTMLTemplateElement)) return;
            const row = institutionTemplate.content.firstElementChild?.cloneNode(true);
            if (!(row instanceof HTMLElement)) return;
            institutionList.append(row);
            addCountryOptions(row.querySelector('[name="institutionCountry"]'));
            refreshInstitutionNumbers();
            return row;
        };

        // 저자 입력 템플릿을 복제하여 새 저자 카드 추가
        const addAuthor = () => {
            if (!(authorList instanceof HTMLElement) || !(authorTemplate instanceof HTMLTemplateElement)) return;
            const card = authorTemplate.content.firstElementChild?.cloneNode(true);
            if (!(card instanceof HTMLElement)) return;
            authorList.append(card);
            const countrySelect = card.querySelector('[name="authorCountry"]');
            addCountryOptions(countrySelect);
            bindAuthorCountryCodes(countrySelect);
            const presenting = card.querySelector('[name="isPresentingAuthor"]');
            if (presenting instanceof HTMLInputElement && authorList.children.length === 1) presenting.checked = true;
            refreshInstitutionNumbers();
            refreshAuthors();
            return card;
        };

        // AI 도구/사용범위 목록을 체크박스 형태로 화면에 생성
        const renderAiOptions = (container, items, type) => {
            if (!(container instanceof HTMLElement)) return;
            container.replaceChildren(...items.map((item) => {
                const label = document.createElement('label');
                const input = document.createElement('input');
                input.type = 'checkbox';
                input.value = String(item.code);
                input.dataset.aiOption = type;
                input.dataset.isEtc = item.isEtc || 'N';
                label.append(input, document.createTextNode(` ${item.name}`));
                return label;
            }));
        };

        // AI 사용 여부 및 기타 항목 선택 상태에 따라 관련 입력필드 활성화/필수값 처리
        const updateAiFields = () => {
            const used = publicAbstractForm.querySelector('[name="aiUsage"]:checked')?.value === 'true';
            publicAbstractForm.querySelectorAll('[data-ai-details]').forEach((row) => {
                row.hidden = !used;
                row.querySelectorAll('input').forEach((input) => input.disabled = !used);
            });
            const version = publicAbstractForm.querySelector('[name="aiVersionInfo"]');
            if (version instanceof HTMLInputElement) version.required = used;

            const selectedOtherTool = publicAbstractForm.querySelector('[data-ai-option="tool"][data-is-etc="Y"]:checked');
            const otherTool = publicAbstractForm.querySelector('[data-other-ai-tool]');
            if (otherTool instanceof HTMLElement) otherTool.hidden = !selectedOtherTool;
            otherTool?.querySelectorAll('input').forEach((input) => input.required = Boolean(selectedOtherTool));

            const selectedOtherScope = publicAbstractForm.querySelector('[data-ai-option="scope"][data-is-etc="Y"]:checked');
            const otherScope = publicAbstractForm.querySelector('[data-other-ai-scope]');
            if (otherScope instanceof HTMLElement) otherScope.hidden = !selectedOtherScope;
            otherScope?.querySelectorAll('input').forEach((input) => input.required = Boolean(selectedOtherScope));
        };

        // 초록 본문(Objective/Methods/Results/Conclusions)의 전체 단어 수 계산
        const abstractWordCount = () => ['objectiveText', 'methodsText', 'resultsText', 'conclusionsText']
            .map((name) => publicAbstractForm.elements.namedItem(name)?.value || '')
            .join(' ')
            .trim()
            .split(/\s+/)
            .filter(Boolean).length;

        // 초록 단어 수를 화면에 표시하고 300단어 초과 여부를 스타일로 표시
        const updateWordCount = () => {
            const count = abstractWordCount();
            if (wordCountOutput) wordCountOutput.textContent = String(count);
            wordCountOutput?.classList.toggle('co-danger', count > 300);
        };

        const value = (root, name) => root.querySelector(`[name="${name}"]`)?.value.trim() || null;
        const checked = (root, name) => Boolean(root.querySelector(`[name="${name}"]`)?.checked);
        const isEnglish = (text) => Array.from(text).every((character) => {
            const code = character.codePointAt(0) || 0;
            return character === '\n' || character === '\r' || character === '\t' || (code >= 0x20 && code <= 0x7e);
        });

        // 지정한 name의 입력 요소에 값을 설정하고 select2가 있으면 UI도 동기화
        const setValue = (root, name, nextValue) => {
            const input = root.querySelector(`[name="${name}"]`);
            if (input instanceof HTMLInputElement || input instanceof HTMLTextAreaElement || input instanceof HTMLSelectElement) {
                input.value = nextValue ?? '';
                if (input instanceof HTMLSelectElement) enhanceSelect(input);
            }
        };

        // 수정 모드에서 기존 초록 데이터를 각 입력필드와 저자/기관 목록에 반영
        const loadExisting = (abstract) => {
            setValue(publicAbstractForm, 'presentationTypeCode', String(abstract.presentationTypeCode || ''));
            setValue(publicAbstractForm, 'categoryCode', String(abstract.categoryCode || ''));
            ['title', 'objectiveText', 'methodsText', 'resultsText', 'conclusionsText', 'aiVersionInfo'].forEach((name) => {
                setValue(publicAbstractForm, name, abstract[name]);
            });
            const aiUsage = publicAbstractForm.querySelector(`[name="aiUsage"][value="${Boolean(abstract.aiUsage)}"]`);
            if (aiUsage instanceof HTMLInputElement) aiUsage.checked = true;
            const compliance = publicAbstractForm.querySelector('[name="plagiarismPolicyConfirmed"]');
            const dataAnalysis = publicAbstractForm.querySelector('[name="aiDataAnalysisUsed"]');
            if (compliance instanceof HTMLInputElement) compliance.checked = Boolean(abstract.plagiarismPolicyConfirmed);
            if (dataAnalysis instanceof HTMLInputElement) dataAnalysis.checked = Boolean(abstract.aiDataAnalysisUsed);

            (abstract.aiTools || []).forEach((tool) => {
                const input = publicAbstractForm.querySelector(`[data-ai-option="tool"][value="${tool.aiToolCode}"]`);
                if (input instanceof HTMLInputElement) input.checked = true;
                if (tool.otherToolName) setValue(publicAbstractForm, 'otherAiToolName', tool.otherToolName);
                if (tool.otherProviderName) setValue(publicAbstractForm, 'otherAiProviderName', tool.otherProviderName);
            });
            (abstract.aiScopes || []).forEach((scope) => {
                const input = publicAbstractForm.querySelector(`[data-ai-option="scope"][value="${scope.aiScopeCode}"]`);
                if (input instanceof HTMLInputElement) input.checked = true;
                if (scope.otherScopeText) setValue(publicAbstractForm, 'otherAiScopeText', scope.otherScopeText);
            });

            institutionList.replaceChildren();
            (abstract.institutions || []).forEach((institution) => {
                const row = addInstitution();
                if (!row) return;
                setValue(row, 'institutionDepartment', institution.department);
                setValue(row, 'institutionName', institution.institutionName);
                setValue(row, 'institutionCountry', institution.country);
            });
            if (!institutionList.children.length) addInstitution();

            authorList.replaceChildren();
            (abstract.authors || []).forEach((author) => {
                const card = addAuthor();
                if (!card) return;
                setValue(card, 'authorName', author.authorName);
                setValue(card, 'authorInstitutionNo', String(author.institutionNo || 1));
                setValue(card, 'authorEmail', author.email);
                setValue(card, 'authorCountry', author.country);
                setValue(card, 'officeCountryCode', author.officeCountryCode);
                setValue(card, 'officePhoneNumber', author.officePhoneNumber);
                setValue(card, 'mobileCountryCode', author.mobileCountryCode);
                setValue(card, 'mobilePhoneNumber', author.mobilePhoneNumber);
                const presenting = card.querySelector('[name="isPresentingAuthor"]');
                const corresponding = card.querySelector('[name="isCorrespondingAuthor"]');
                if (presenting instanceof HTMLInputElement) presenting.checked = Boolean(author.isPresentingAuthor);
                if (corresponding instanceof HTMLInputElement) corresponding.checked = Boolean(author.isCorrespondingAuthor);
            });
            if (!authorList.children.length) addAuthor();

            const submissionNo = publicAbstractForm.querySelector('[data-submission-no]');
            const formTitle = publicAbstractForm.querySelector('[data-abstract-form-title]');
            if (submissionNo instanceof HTMLInputElement) submissionNo.value = abstract.submissionNo || '-';
            if (formTitle) formTitle.textContent = window.PublicSite.t('Abstract Submission Form');
            const draftButton = publicAbstractForm.querySelector('[data-status="draft"]');
            if (draftButton instanceof HTMLButtonElement) draftButton.hidden = abstract.status !== 'draft';
            if (!['draft', 'submitted'].includes(abstract.status)) {
                window.location.assign(window.PublicSite.pageUrl(`/abstract-review?seq=${abstract.seq}`));
                return;
            }
            updateAiFields();
            refreshInstitutionNumbers();
            refreshAuthors();
            updateWordCount();
        };

        // 초록 저장 API로 전송할 JSON 데이터를 현재 폼 값으로 생성
        const payload = (status) => ({
            presentationTypeCode: Number(presentationType.value),
            categoryCode: Number(category.value),
            title: value(publicAbstractForm, 'title'),
            objectiveText: value(publicAbstractForm, 'objectiveText'),
            methodsText: value(publicAbstractForm, 'methodsText'),
            resultsText: value(publicAbstractForm, 'resultsText'),
            conclusionsText: value(publicAbstractForm, 'conclusionsText'),
            aiUsage: publicAbstractForm.querySelector('[name="aiUsage"]:checked')?.value === 'true',
            aiVersionInfo: value(publicAbstractForm, 'aiVersionInfo'),
            aiDataAnalysisUsed: checked(publicAbstractForm, 'aiDataAnalysisUsed'),
            plagiarismPolicyConfirmed: checked(publicAbstractForm, 'plagiarismPolicyConfirmed'),
            status,
            aiTools: Array.from(publicAbstractForm.querySelectorAll('[data-ai-option="tool"]:checked')).map((input) => ({
                aiToolCode: Number(input.value),
                otherToolName: input.dataset.isEtc === 'Y' ? value(publicAbstractForm, 'otherAiToolName') : null,
                otherProviderName: input.dataset.isEtc === 'Y' ? value(publicAbstractForm, 'otherAiProviderName') : null
            })),
            aiScopes: Array.from(publicAbstractForm.querySelectorAll('[data-ai-option="scope"]:checked')).map((input) => ({
                aiScopeCode: Number(input.value),
                otherScopeText: input.dataset.isEtc === 'Y' ? value(publicAbstractForm, 'otherAiScopeText') : null
            })),
            institutions: Array.from(institutionList.children).map((row, index) => ({
                institutionNo: index + 1,
                department: value(row, 'institutionDepartment'),
                institutionName: value(row, 'institutionName'),
                country: value(row, 'institutionCountry')
            })),
            authors: Array.from(authorList.children).map((card, index) => ({
                authorOrder: index + 1,
                authorName: value(card, 'authorName'),
                institutionNo: Number(value(card, 'authorInstitutionNo')),
                isPresentingAuthor: checked(card, 'isPresentingAuthor'),
                isCorrespondingAuthor: checked(card, 'isCorrespondingAuthor'),
                email: value(card, 'authorEmail'),
                country: value(card, 'authorCountry'),
                officeCountryCode: value(card, 'officeCountryCode'),
                officePhoneNumber: value(card, 'officePhoneNumber'),
                mobileCountryCode: value(card, 'mobileCountryCode'),
                mobilePhoneNumber: value(card, 'mobilePhoneNumber')
            }))
        });

        // 초록 폼의 선택값 변경 시 AI 관련 필드 및 저자 역할 표시를 즉시 갱신
        publicAbstractForm.addEventListener('change', (event) => {
            if (event.target instanceof HTMLInputElement && (event.target.name === 'aiUsage' || event.target.dataset.aiOption)) updateAiFields();
            if (event.target instanceof HTMLInputElement && event.target.name === 'isPresentingAuthor') refreshAuthors();
        });
        publicAbstractForm.addEventListener('input', (event) => {
            if (event.target instanceof HTMLTextAreaElement) updateWordCount();
            if (event.target instanceof HTMLInputElement && ['institutionName', 'institutionDepartment'].includes(event.target.name)) refreshInstitutionNumbers();
        });
        publicAbstractForm.querySelector('[data-add-institution]')?.addEventListener('click', addInstitution);
        publicAbstractForm.querySelector('[data-add-author]')?.addEventListener('click', addAuthor);
        institutionList?.addEventListener('click', (event) => {
            const button = event.target instanceof Element ? event.target.closest('[data-remove-institution]') : null;
            if (button && institutionList.children.length > 1) {
                button.closest('tr')?.remove();
                refreshInstitutionNumbers();
            }
        });
        authorList?.addEventListener('click', (event) => {
            const button = event.target instanceof Element ? event.target.closest('button') : null;
            const card = button?.closest('.abstract-author-card');
            if (!button || !card) return;
            if (button.matches('[data-remove-author]') && authorList.children.length > 1) card.remove();
            if (button.matches('[data-move-author="up"]') && card.previousElementSibling) card.previousElementSibling.before(card);
            if (button.matches('[data-move-author="down"]') && card.nextElementSibling) card.nextElementSibling.after(card);
            refreshAuthors();
        });

        addInstitution();
        addAuthor();
        updateAiFields();

        Promise.all([
            fetch(window.PublicSite.apiUrl('/api/public/abstracts/meta')).then(async (response) => {
                if (!response.ok) throw new Error(await window.PublicSite.responseMessage(response) || window.PublicSite.t('Abstract form options could not be loaded.'));
                return response.json();
            }),
            fetch('/api/countries/used').then(async (response) => {
                if (!response.ok) throw new Error(await window.PublicSite.responseMessage(response) || window.PublicSite.t('Country list could not be loaded.'));
                return response.json();
            })
        ]).then(async ([meta, countryItems]) => {
            countries = countryItems;
            addOptions(presentationType, meta.presentationTypes, window.PublicSite.t('Select a presentation type'));
            addOptions(category, meta.categories, window.PublicSite.t('Select a category'));
            renderAiOptions(aiTools, meta.aiTools, 'tool');
            renderAiOptions(aiScopes, meta.aiScopes, 'scope');
            publicAbstractForm.querySelectorAll('[name="institutionCountry"], [name="authorCountry"]').forEach(addCountryOptions);
            updateAiFields();
            if (editingSeq) {
                const response = await fetch(window.PublicSite.apiUrl(`/api/public/abstracts/${encodeURIComponent(editingSeq)}`));
                if (response.status === 401) {
                    window.location.assign(window.PublicSite.pageUrl('/login'));
                    return;
                }
                if (!response.ok) throw new Error(await window.PublicSite.responseMessage(response) || window.PublicSite.t('Abstract submission could not be loaded.'));
                loadExisting(await response.json());
            }
        }).catch((error) => setStatus(error instanceof Error ? error.message : window.PublicSite.t('Abstract form could not be loaded.'), true));

        // 초록 저장/제출 시 필수값, 발표저자, AI 사용정보, 단어 수, 영문 입력 여부를 검사한 뒤 API 호출
        publicAbstractForm.addEventListener('submit', async (event) => {
            event.preventDefault();
            setStatus('');
            const submitter = event.submitter instanceof HTMLButtonElement ? event.submitter : null;
            const status = submitter?.dataset.status || 'submitted';

            if (!publicAbstractForm.reportValidity()) return;
            if (!publicAbstractForm.querySelector('[name="isPresentingAuthor"]:checked')) {
                window.alert(window.PublicSite.t('Select at least one presenting author.'));
                return;
            }
            if (publicAbstractForm.querySelector('[name="aiUsage"]:checked')?.value === 'true'
                && (!publicAbstractForm.querySelector('[data-ai-option="tool"]:checked') || !publicAbstractForm.querySelector('[data-ai-option="scope"]:checked'))) {
                window.alert(window.PublicSite.t('Select at least one AI tool and scope of use.'));
                return;
            }
            if (abstractWordCount() > 300) {
                window.alert(window.PublicSite.t('Abstract content must be 300 words or less.'));
                return;
            }
            const englishFields = ['title', 'objectiveText', 'methodsText', 'resultsText', 'conclusionsText'];
            if (englishFields.some((name) => !isEnglish(publicAbstractForm.elements.namedItem(name)?.value || ''))) {
                window.alert(window.PublicSite.t('Title and abstract content must contain English characters only.'));
                return;
            }

            const buttons = publicAbstractForm.querySelectorAll('button[type="submit"]');
            buttons.forEach((button) => button.disabled = true);
            const originalText = submitter?.textContent;
            if (submitter) submitter.textContent = status === 'draft' ? window.PublicSite.t('Saving...') : window.PublicSite.t('Submitting...');

            try {
                const csrf = publicAbstractForm.querySelector('[name="_csrf"]');
                const response = await fetch(editingSeq ? window.PublicSite.apiUrl(`/abstracts/${encodeURIComponent(editingSeq)}`) : publicAbstractForm.action, {
                    method: editingSeq ? 'PUT' : 'POST',
                    headers: {
                        'Accept': 'application/json',
                        'Content-Type': 'application/json',
                        ...(csrf instanceof HTMLInputElement ? {'X-CSRF-TOKEN': csrf.value} : {})
                    },
                    body: JSON.stringify(payload(status))
                });
                if (response.status === 401) {
                    window.alert(window.PublicSite.t('Please log in before submitting an abstract.'));
                    window.location.assign(window.PublicSite.pageUrl('/login'));
                    return;
                }
                if (!response.ok) throw new Error(await window.PublicSite.responseMessage(response) || window.PublicSite.t('Abstract submission failed.'));
                const saved = await response.json();
                window.alert(window.PublicSite.t(status === 'draft' ? window.PublicSite.t('Draft {number} was saved.') : window.PublicSite.t('Abstract {number} was submitted.'), {number: saved.submissionNo}));
                window.location.assign(window.PublicSite.pageUrl('/mypage-abstract'));
            } catch (error) {
                setStatus(error instanceof Error ? error.message : window.PublicSite.t('Abstract submission failed.'), true);
                window.alert(error instanceof Error ? error.message : window.PublicSite.t('Abstract submission failed.'));
            } finally {
                buttons.forEach((button) => button.disabled = false);
                if (submitter) submitter.textContent = originalText || (status === 'draft' ? window.PublicSite.t('Temporary Save') : window.PublicSite.t('Submit'));
            }
        });
    }

    // 초록 상세 조회 및 승인된 초록의 발표자료 업로드/삭제 관리
    const publicAbstractReview = document.querySelector('#public-abstract-review');

    if (publicAbstractReview instanceof HTMLElement) {
        const seq = new URLSearchParams(window.location.search).get('seq');
        const loading = publicAbstractReview.querySelector('[data-review-loading]');
        const content = publicAbstractReview.querySelector('[data-review-content]');
        const editLink = publicAbstractReview.querySelector('[data-review-edit]');
        const abstractDeleteButton = publicAbstractReview.querySelector('[data-delete-abstract]');
        const abstractDeleteDialog = document.querySelector('[data-abstract-delete-dialog]');
        const confirmAbstractDelete = abstractDeleteDialog?.querySelector('[data-confirm-abstract-delete]');
        const materialSection = publicAbstractReview.querySelector('[data-presentation-materials]');
        const uploadForm = publicAbstractReview.querySelector('[data-presentation-upload-form]');
        const fileInput = publicAbstractReview.querySelector('#presentation-material-file');
        const fileName = publicAbstractReview.querySelector('[data-presentation-file-name]');
        const attachmentBody = publicAbstractReview.querySelector('[data-presentation-attachments]');
        const materialStatus = publicAbstractReview.querySelector('[data-presentation-status]');
        const deleteDialog = document.querySelector('[data-presentation-delete-dialog]');
        const confirmDelete = deleteDialog?.querySelector('[data-confirm-presentation-delete]');
        let currentAbstract;
        let pendingAttachmentSeq;
        const statusLabels = {
            draft: window.PublicSite.t('Draft'),
            submitted: window.PublicSite.t('Submission Completed'),
            under_review: window.PublicSite.t('Under Review'),
            approved: window.PublicSite.t('Accepted'),
            rejected: window.PublicSite.t('Rejected')
        };
        // 초록 상세 화면의 data-review-field 영역에 값을 출력
        const text = (field, value) => {
            const target = publicAbstractReview.querySelector(`[data-review-field="${field}"]`);
            if (target) target.textContent = value || '-';
        };
        // 전달받은 배열 데이터로 상세 화면의 테이블 행을 동적으로 생성
        const fillRows = (selector, rows) => {
            const body = publicAbstractReview.querySelector(selector);
            if (!(body instanceof HTMLTableSectionElement)) return;

            body.replaceChildren(...rows.map((values) => {
                const row = document.createElement('tr');
                values.forEach((value) => {
                    const cell = document.createElement('td');
                    cell.textContent = value || '-';
                    row.append(cell);
                });
                return row;
            }));
        };
        // POST/PUT/DELETE 요청에 사용할 CSRF 헤더 생성
        const csrfHeaders = () => {
            const csrf = uploadForm?.querySelector('[name="_csrf"]');
            return csrf instanceof HTMLInputElement ? {'X-CSRF-TOKEN': csrf.value} : {};
        };
        // 발표자료 업로드/삭제 처리 상태 메시지를 표시
        const setMaterialStatus = (message, error = false) => {
            if (!(materialStatus instanceof HTMLElement)) return;
            materialStatus.textContent = message || '';
            materialStatus.hidden = !message;
            materialStatus.classList.toggle('is-error', error);
        };
        // 파일 크기(byte)를 B/KB/MB 단위의 읽기 쉬운 문자열로 변환
        const formatFileSize = (bytes) => {
            const size = Number(bytes || 0);
            if (size < 1024) return `${size} B`;
            if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`;
            return `${(size / 1024 / 1024).toFixed(1)} MB`;
        };
        // 현재 초록에 등록된 발표자료 첨부파일 목록을 테이블에 출력
        const renderAttachments = () => {
            if (!(attachmentBody instanceof HTMLTableSectionElement)) return;
            const attachments = currentAbstract?.attachments || [];
            if (!attachments.length) {
                const row = document.createElement('tr');
                const cell = document.createElement('td');
                cell.colSpan = 3;
                cell.className = 'member-table-empty';
                cell.textContent = window.PublicSite.t('No presentation material has been uploaded.');
                row.append(cell);
                attachmentBody.replaceChildren(row);
                return;
            }

            attachmentBody.replaceChildren(...attachments.map((attachment) => {
                const row = document.createElement('tr');
                const nameCell = document.createElement('td');
                nameCell.textContent = attachment.originalFilename || '-';
                const sizeCell = document.createElement('td');
                sizeCell.textContent = formatFileSize(attachment.fileSize);
                const actionCell = document.createElement('td');
                const actions = document.createElement('div');
                actions.className = 'member-row-actions';
                const download = document.createElement('a');
                download.className = 'member-button member-button--outline member-button--compact';
                download.href = window.PublicSite.apiUrl(`/api/public/abstracts/${encodeURIComponent(currentAbstract.seq)}/attachments/${encodeURIComponent(attachment.seq)}`);
                download.target = '_blank';
                download.rel = 'noopener noreferrer';
                download.textContent = window.PublicSite.t('Download');
                const remove = document.createElement('button');
                remove.className = 'member-button member-button--soft member-button--compact';
                remove.type = 'button';
                remove.dataset.deleteAttachment = String(attachment.seq);
                remove.setAttribute('aria-label', `Delete ${attachment.originalFilename || 'presentation material'}`);
                remove.textContent = window.PublicSite.t('Delete');
                actions.append(download, remove);
                actionCell.append(actions);
                row.append(nameCell, sizeCell, actionCell);
                return row;
            }));
        };

        fileInput?.addEventListener('change', () => {
            if (fileName instanceof HTMLElement) {
                fileName.textContent = fileInput instanceof HTMLInputElement && fileInput.files?.[0]
                    ? fileInput.files[0].name
                    : window.PublicSite.t('No file selected.');
            }
        });
        // 발표자료 업로드: 확장자·100MB 제한·최대 5개 여부 확인 후 서버로 전송
        uploadForm?.addEventListener('submit', async (event) => {
            event.preventDefault();
            if (!(fileInput instanceof HTMLInputElement) || !fileInput.files?.[0] || !currentAbstract) return;
            const file = fileInput.files[0];
            const extension = file.name.split('.').pop()?.toLowerCase();
            if (!['pdf', 'ppt', 'pptx'].includes(extension || '')) {
                setMaterialStatus(window.PublicSite.t('Please select a PDF, PPT, or PPTX file.'), true);
                return;
            }
            if (file.size > 100 * 1024 * 1024) {
                setMaterialStatus(window.PublicSite.t('Presentation materials must be 100MB or smaller.'), true);
                return;
            }
            if ((currentAbstract.attachments || []).length >= 5) {
                setMaterialStatus(window.PublicSite.t('Up to 5 presentation materials can be uploaded.'), true);
                return;
            }

            const submit = uploadForm.querySelector('button[type="submit"]');
            if (submit instanceof HTMLButtonElement) submit.disabled = true;
            setMaterialStatus(window.PublicSite.t('Uploading...'));
            try {
                const body = new FormData();
                body.append('file', file);
                const response = await fetch(window.PublicSite.apiUrl(`/api/public/abstracts/${encodeURIComponent(currentAbstract.seq)}/attachments`), {
                    method: 'POST', headers: csrfHeaders(), body
                });
                if (response.status === 401) {
                    window.location.assign(window.PublicSite.pageUrl('/login'));
                    return;
                }
                if (!response.ok) throw new Error(await window.PublicSite.responseMessage(response) || window.PublicSite.t('Presentation material could not be uploaded.'));
                currentAbstract.attachments = [...(currentAbstract.attachments || []), await response.json()];
                uploadForm.reset();
                if (fileName instanceof HTMLElement) fileName.textContent = window.PublicSite.t('No file selected.');
                renderAttachments();
                setMaterialStatus(window.PublicSite.t('Presentation material uploaded.'));
            } catch (error) {
                setMaterialStatus(error instanceof Error ? error.message : window.PublicSite.t('Presentation material could not be uploaded.'), true);
            } finally {
                if (submit instanceof HTMLButtonElement) submit.disabled = false;
            }
        });
        attachmentBody?.addEventListener('click', (event) => {
            const button = event.target instanceof Element ? event.target.closest('[data-delete-attachment]') : null;
            if (!(button instanceof HTMLButtonElement)) return;
            pendingAttachmentSeq = button.dataset.deleteAttachment;
            if (deleteDialog instanceof HTMLDialogElement) deleteDialog.showModal();
        });
        // 선택한 발표자료 삭제 확정 처리
        confirmDelete?.addEventListener('click', async () => {
            if (!currentAbstract || !pendingAttachmentSeq) return;
            if (deleteDialog instanceof HTMLDialogElement) deleteDialog.close();
            setMaterialStatus(window.PublicSite.t('Deleting...'));
            try {
                const response = await fetch(window.PublicSite.apiUrl(`/api/public/abstracts/${encodeURIComponent(currentAbstract.seq)}/attachments/${encodeURIComponent(pendingAttachmentSeq)}`), {
                    method: 'DELETE', headers: csrfHeaders()
                });
                if (response.status === 401) {
                    window.location.assign(window.PublicSite.pageUrl('/login'));
                    return;
                }
                if (!response.ok) throw new Error(await window.PublicSite.responseMessage(response) || window.PublicSite.t('Presentation material could not be deleted.'));
                currentAbstract.attachments = (currentAbstract.attachments || [])
                    .filter((attachment) => String(attachment.seq) !== String(pendingAttachmentSeq));
                renderAttachments();
                setMaterialStatus(window.PublicSite.t('Presentation material deleted.'));
            } catch (error) {
                setMaterialStatus(error instanceof Error ? error.message : window.PublicSite.t('Presentation material could not be deleted.'), true);
            } finally {
                pendingAttachmentSeq = undefined;
            }
        });
        abstractDeleteButton?.addEventListener('click', () => {
            if (abstractDeleteDialog instanceof HTMLDialogElement) abstractDeleteDialog.showModal();
        });
        // 임시저장 상태의 초록 삭제 확정 처리
        confirmAbstractDelete?.addEventListener('click', async () => {
            if (!currentAbstract || !(confirmAbstractDelete instanceof HTMLButtonElement)) return;
            confirmAbstractDelete.disabled = true;
            try {
                const response = await fetch(window.PublicSite.apiUrl(`/api/public/abstracts/${encodeURIComponent(currentAbstract.seq)}`), {
                    method: 'DELETE', headers: csrfHeaders()
                });
                if (response.status === 401) {
                    window.location.assign(window.PublicSite.pageUrl('/login'));
                    return;
                }
                if (!response.ok) throw new Error(await window.PublicSite.responseMessage(response) || window.PublicSite.t('Draft deletion failed.'));
                window.location.assign(window.PublicSite.pageUrl('/mypage-abstract'));
            } catch (error) {
                window.alert(error instanceof Error ? error.message : window.PublicSite.t('Draft deletion failed. Please try again.'));
                confirmAbstractDelete.disabled = false;
            }
        });

        if (!seq) {
            window.location.assign(window.PublicSite.pageUrl('/mypage-abstract'));
        } else {
            fetch(window.PublicSite.apiUrl(`/api/public/abstracts/${encodeURIComponent(seq)}`))
                .then(async (response) => {
                    if (response.status === 401) {
                        window.location.assign(window.PublicSite.pageUrl('/login'));
                        return null;
                    }
                    if (!response.ok) throw new Error(await window.PublicSite.responseMessage(response) || window.PublicSite.t('Abstract submission could not be loaded.'));
                    return response.json();
                })
                .then((abstract) => {
                    if (!abstract) return;
                    currentAbstract = abstract;
                    text('submissionNo', abstract.submissionNo);
                    text('status', statusLabels[abstract.status] || abstract.status);
                    text('presentationType', abstract.presentationTypeName || String(abstract.presentationTypeCode));
                    text('category', abstract.categoryName || String(abstract.categoryCode));
                    text('title', abstract.title);
                    text('objectiveText', abstract.objectiveText);
                    text('methodsText', abstract.methodsText);
                    text('resultsText', abstract.resultsText);
                    text('conclusionsText', abstract.conclusionsText);
                    text('aiUsage', abstract.aiUsage ? window.PublicSite.t('Yes') : window.PublicSite.t('No'));
                    text('aiTools', abstract.aiUsage ? (abstract.aiTools || []).map((tool) => [tool.otherToolName || tool.aiToolName, tool.otherProviderName].filter(Boolean).join(' · ')).filter(Boolean).join(', ') : window.PublicSite.t('Not used'));
                    text('aiVersionInfo', abstract.aiUsage ? abstract.aiVersionInfo : window.PublicSite.t('Not used'));
                    text('aiScopes', abstract.aiUsage ? (abstract.aiScopes || []).map((scope) => scope.otherScopeText || scope.aiScopeName).filter(Boolean).join(', ') : window.PublicSite.t('Not used'));
                    text('aiDataAnalysisUsed', abstract.aiDataAnalysisUsed ? window.PublicSite.t('Yes') : window.PublicSite.t('No'));
                    text('plagiarismPolicyConfirmed', abstract.plagiarismPolicyConfirmed ? window.PublicSite.t('Yes') : window.PublicSite.t('No'));
                    const institutions = new Map((abstract.institutions || []).map((institution) => [institution.institutionNo, institution]));
                    fillRows('[data-review-authors]', (abstract.authors || []).map((author) => [
                        String(author.authorOrder)
                        , author.authorName
                        , [author.isPresentingAuthor ? window.PublicSite.t('Presenting Author') : '', author.isCorrespondingAuthor ? window.PublicSite.t('Corresponding author') : ''].filter(Boolean).join(', ') || window.PublicSite.t('Author')
                        , [institutions.get(author.institutionNo)?.institutionName, institutions.get(author.institutionNo)?.country].filter(Boolean).join(', ')
                        , institutions.get(author.institutionNo)?.department
                        , author.email
                    ]));
                    if (loading instanceof HTMLElement) loading.hidden = true;
                    if (content instanceof HTMLElement) content.hidden = false;
                    if (editLink instanceof HTMLAnchorElement && ['draft', 'submitted'].includes(abstract.status)) {
                        editLink.href = window.PublicSite.pageUrl(`/abstract-write?seq=${abstract.seq}`);
                        editLink.hidden = false;
                    }
                    // 삭제 기능은 목록에 노출하지 않고 임시저장 상태의 상세 화면에서만 제공
                    if (abstractDeleteButton instanceof HTMLButtonElement && abstract.status === 'draft') {
                        abstractDeleteButton.hidden = false;
                    }
                    if (materialSection instanceof HTMLElement && abstract.status === 'approved') {
                        materialSection.hidden = false;
                        renderAttachments();
                    }
                })
                .catch((error) => {
                    if (loading instanceof HTMLElement) {
                        loading.textContent = error instanceof Error ? error.message : window.PublicSite.t('Abstract submission could not be loaded.');
                        loading.classList.add('co-danger');
                    }
                });
        }
    }

    // Registration 등록/수정 및 결제 연동 처리. 참가비·옵션 정보는 /form API 응답을 기준으로 사용
    const publicRegistrationForm = document.querySelector('#public-registration-form');

    if (publicRegistrationForm instanceof HTMLFormElement) {
        const categoryList = publicRegistrationForm.querySelector('[data-registration-categories]');
        const paymentMethod = publicRegistrationForm.querySelector('#registration-payment-method');
        const feeText = publicRegistrationForm.querySelector('#registration-fee');
        const amountText = publicRegistrationForm.querySelector('#registration-amount');
        const optionRow = publicRegistrationForm.querySelector('[data-registration-options-row]');
        const optionList = publicRegistrationForm.querySelector('[data-registration-options]');
        const agreements = publicRegistrationForm.querySelector('[data-registration-agreements]');
        const statusBox = publicRegistrationForm.querySelector('[data-registration-status]');
        const submitButton = publicRegistrationForm.querySelector('button[type="submit"]');
        const cancelButton = publicRegistrationForm.querySelector('[data-cancel-registration]');
        const cancelDialog = document.querySelector('[data-registration-cancel-dialog]');
        const confirmCancelButton = cancelDialog?.querySelector('[data-confirm-registration-cancel]');
        const refundButton = publicRegistrationForm.querySelector('[data-request-registration-refund]');
        const refundDialog = document.querySelector('[data-registration-refund-dialog]');
        const refundItems = refundDialog?.querySelector('[data-registration-refund-items]');
        const refundTotal = refundDialog?.querySelector('[data-registration-refund-total]');
        const submitRefundButton = refundDialog?.querySelector('[data-submit-registration-refund]');
        const csrf = publicRegistrationForm.querySelector('[name="_csrf"]');
        let formData;
        let currentRegistration;

        const csrfHeaders = () => csrf instanceof HTMLInputElement ? {'X-CSRF-TOKEN': csrf.value} : {};
        const setStatus = (message, error = false) => {
            if (!(statusBox instanceof HTMLElement)) return;
            statusBox.textContent = message || '';
            statusBox.hidden = !message;
            statusBox.classList.toggle('is-error', error);
        };
        // Registration 화면의 회원 기본정보 영역에 값 출력
        const setMemberText = (name, value) => {
            const target = publicRegistrationForm.querySelector(`[data-registration-member="${name}"]`);
            if (target instanceof HTMLElement) target.textContent = value || '-';
        };
        const enhanceSelect = (select) => {
            if (!(select instanceof HTMLSelectElement) || !window.jQuery?.fn.select2) return;
            const target = window.jQuery(select);
            if (target.hasClass('select2-hidden-accessible')) target.trigger('change.select2');
            else target.select2({
                placeholder: select.options[0]?.text || 'Select',
                minimumResultsForSearch: select.name === 'country' ? 0 : Infinity,
                width: '100%'
            });
        };
        // 금액과 통화코드를 기준으로 KRW/USD 등의 통화 형식 문자열 생성
        const money = (amount, currency) => {
            if (amount === null || amount === undefined || !currency) return '';
            return new Intl.NumberFormat(currency === 'KRW' ? 'ko-KR' : 'en-US', {
                style: 'currency', currency, maximumFractionDigits: currency === 'KRW' ? 0 : 2
            }).format(Number(amount));
        };
        // 현재 선택된 Registration 참가 구분 radio 요소 반환
        const selectedCategoryInput = () => categoryList?.querySelector('input[name="categorySeq"]:checked');
        // 현재 선택된 참가 구분에 해당하는 서버 데이터 반환
        const selectedCategory = () => {
            const input = selectedCategoryInput();
            return formData?.categories?.find((item) => input instanceof HTMLInputElement
                && String(item.categorySeq) === input.value);
        };
        // 수량이 1개 이상 선택된 추가 옵션만 추출
        const selectedOptions = () => Array.from(optionList?.querySelectorAll('[data-option-quantity]') || [])
            .map((input) => ({optionSeq: Number(input.dataset.optionSeq), quantity: Number(input.value)}))
            .filter((item) => item.quantity > 0);
        // 기본 참가비와 선택한 추가 옵션 금액을 합산하여 총 결제금액 계산
        const calculatedTotal = () => {
            const category = selectedCategory();
            if (!category) return null;
            return selectedOptions().reduce((total, selection) => {
                const option = formData.options.find((item) => item.optionSeq === selection.optionSeq);
                return total + Number(option?.unitPrice || 0) * selection.quantity;
            }, Number(category.feeAmount));
        };
        // 선택한 참가 구분/옵션에 따라 참가비와 총 결제금액 및 버튼 상태 갱신
        const refreshAmount = () => {
            const category = selectedCategory();
            const total = calculatedTotal();
            if (feeText instanceof HTMLElement) feeText.textContent = category ? money(category.feeAmount, formData.currency) : '';
            if (amountText instanceof HTMLElement) amountText.textContent = total === null ? '' : money(total, formData.currency);
            if (submitButton instanceof HTMLButtonElement
                && (!currentRegistration || currentRegistration.paymentStatus === 'UNPAID')) {
                submitButton.disabled = !formData.registrationOpen || !category || (total > 0 && !formData.paymentAvailable);
            }
        };
        // Registration 참가 구분 선택용 radio + label 요소 생성
        const createCategoryChoice = (category) => {
            const label = document.createElement('label');
            const input = document.createElement('input');
            input.type = 'radio';
            input.name = 'categorySeq';
            input.value = String(category.categorySeq);
            input.required = true;
            label.append(input, document.createTextNode(category.categoryName));
            return label;
        };
        // 신규/미결제 Registration에서 선택 가능한 추가 옵션 목록 생성
        const renderOptions = (options, selections = []) => {
            if (!(optionList instanceof HTMLElement) || !(optionRow instanceof HTMLTableRowElement)) return;
            const selectedQuantities = new Map(selections.map((option) => [option.optionSeq, option.quantity]));
            optionRow.hidden = !options.length;
            optionList.replaceChildren(...options.map((option) => {
                const label = document.createElement('label');
                label.className = 'registration-option-row';
                const details = document.createElement('span');
                const name = document.createElement('strong');
                const description = document.createElement('small');
                name.textContent = option.optionName;
                description.textContent = `${option.description || ''}${option.description ? ' · ' : ''}${money(option.unitPrice, option.currency)}`;
                details.append(name, description);
                const quantity = document.createElement('input');
                const selectedQuantity = selectedQuantities.get(option.optionSeq) || 0;
                quantity.type = 'number';
                quantity.min = '0';
                quantity.max = String(Math.min(
                    option.maxPerPerson || 1,
                    (option.remainingCapacity ?? option.maxPerPerson ?? 1) + selectedQuantity
                ));
                quantity.value = String(selectedQuantity);
                quantity.dataset.optionQuantity = '';
                quantity.dataset.optionSeq = String(option.optionSeq);
                quantity.setAttribute('aria-label', `${option.optionName} quantity`);
                label.append(details, quantity);
                return label;
            }));
        };
        // 결제 완료 등 수정 불가능한 Registration의 기존 옵션을 읽기전용으로 출력
        const renderExistingOptions = (options) => {
            if (!(optionList instanceof HTMLElement) || !(optionRow instanceof HTMLTableRowElement)) return;
            optionRow.hidden = !options.length;
            optionList.replaceChildren(...options.map((option) => {
                const row = document.createElement('div');
                row.className = 'registration-option-row';
                const details = document.createElement('span');
                const name = document.createElement('strong');
                const description = document.createElement('small');
                name.textContent = option.optionName;
                description.textContent = `${option.optionDescription || ''}${option.optionDescription ? ' · ' : ''}${window.PublicSite.t('Quantity: {quantity}', {quantity: option.quantity})}`;
                details.append(name, description);
                row.append(details);
                return row;
            }));
        };
        // 환불 선택 항목의 금액을 합산하고 환불 요청 버튼 상태 갱신
        const refreshRefundTotal = () => {
            const selected = Array.from(refundItems?.querySelectorAll('[data-refund-amount]:checked') || []);
            const total = selected.reduce((sum, input) => sum + Number(input.dataset.refundAmount), 0);
            if (refundTotal instanceof HTMLElement) refundTotal.textContent = money(total, currentRegistration?.currency);
            if (submitRefundButton instanceof HTMLButtonElement) submitRefundButton.disabled = !selected.length;
        };
        // 환불 대상 항목 한 건의 체크박스 UI 생성
        const createRefundItem = (name, description, amount, registrationFee = false) => {
            const label = document.createElement('label');
            label.className = 'registration-refund-item';
            const input = document.createElement('input');
            input.type = 'checkbox';
            input.dataset.refundAmount = String(amount || 0);
            if (registrationFee) input.dataset.refundRegistrationFee = '';
            const details = document.createElement('span');
            const title = document.createElement('strong');
            const detail = document.createElement('small');
            const price = document.createElement('b');
            title.textContent = name;
            detail.textContent = description;
            price.textContent = money(amount || 0, currentRegistration?.currency);
            details.append(title, detail);
            label.append(input, details, price);
            return label;
        };
        // Registration 참가비와 옵션을 환불 선택 목록으로 구성
        const renderRefundItems = (registration) => {
            if (!(refundItems instanceof HTMLElement)) return;
            refundItems.replaceChildren(
                createRefundItem('Registration Fee', registration.categoryName, registration.feeAmount, true),
                ...(registration.options || []).map((option) => createRefundItem(
                    option.optionName,
                    `Quantity: ${option.quantity}`,
                    option.amount
                ))
            );
            const reason = refundDialog?.querySelector('textarea');
            if (reason instanceof HTMLTextAreaElement) reason.value = '';
            refreshRefundTotal();
        };
        // 기존 Registration 정보를 폼에 반영하고 결제 상태에 따라 수정 가능 여부 설정
        const showExisting = (registration) => {
            currentRegistration = registration;
            if (!(categoryList instanceof HTMLElement)) return;
            const paid = registration.paymentStatus === 'PAID';
            const editable = registration.paymentStatus === 'UNPAID';
            if (paid) {
                const category = document.createElement('span');
                category.textContent = registration.categoryName;
                categoryList.replaceChildren(category);
                if (paymentMethod instanceof HTMLElement) {
                    const method = document.createElement('span');
                    method.textContent = window.PublicSite.t('Credit Card');
                    paymentMethod.replaceChildren(method);
                }
            } else {
                let selected = Array.from(categoryList.querySelectorAll('input[name="categorySeq"]'))
                    .find((input) => input.value === String(registration.categorySeq));
                if (!(selected instanceof HTMLInputElement)) {
                    const choice = createCategoryChoice(registration);
                    categoryList.append(choice);
                    selected = choice.querySelector('input');
                }
                if (selected instanceof HTMLInputElement) selected.checked = true;
                categoryList.querySelectorAll('input[name="categorySeq"]')
                    .forEach((input) => input.disabled = !editable);
                paymentMethod?.querySelectorAll('input').forEach((input) => input.disabled = !editable);
            }
            if (feeText instanceof HTMLElement) feeText.textContent = money(registration.feeAmount, registration.currency);
            if (amountText instanceof HTMLElement) amountText.textContent = money(registration.totalAmount, registration.currency);
            if (editable) {
                renderOptions(formData.options || [], registration.options || []);
                refreshAmount();
            } else {
                renderExistingOptions(registration.options || []);
            }
            if (agreements instanceof HTMLElement) {
                agreements.hidden = true;
                agreements.querySelectorAll('input').forEach((input) => input.disabled = true);
            }
            if (submitButton instanceof HTMLButtonElement) {
                submitButton.textContent = paid ? window.PublicSite.t('Registration Completed')
                    : editable ? window.PublicSite.t('Save & Continue to Payment') : window.PublicSite.t('Continue to Payment');
                const total = editable ? calculatedTotal() : Number(registration.totalAmount);
                submitButton.disabled = paid || !formData.registrationOpen
                    || (editable && !selectedCategory())
                    || (total > 0 && !formData.paymentAvailable);
            }
            if (cancelButton instanceof HTMLButtonElement) {
                cancelButton.hidden = !['UNPAID', 'FAILED'].includes(registration.paymentStatus);
                cancelButton.disabled = false;
            }
            if (refundButton instanceof HTMLButtonElement) {
                refundButton.hidden = !paid || !publicRegistrationForm.closest('.member-page');
                refundButton.disabled = false;
            }
        };
        const renderForm = (data) => {
            formData = data;
            setMemberText('name', [data.member.firstName, data.member.lastName].filter(Boolean).join(' '));
            setMemberText('institution', data.member.institution);
            setMemberText('country', data.member.country);
            setMemberText('email', data.member.email);
            setMemberText('mobile', data.member.mobile);
            if (categoryList instanceof HTMLElement) {
                categoryList.replaceChildren(...data.categories.map(createCategoryChoice));
                categoryList.querySelectorAll('input[name="categorySeq"]')
                    .forEach((input) => input.disabled = !data.registrationOpen);
            }
            renderOptions(data.options || []);
            if (data.registration) showExisting(data.registration);
            else refreshAmount();
            publicRegistrationForm.querySelectorAll('select').forEach(enhanceSelect);
            setStatus(data.message || '');
        };
        const requestJson = async (url, options = {}) => {
            const response = await fetch(url, options);
            if (response.status === 401) {
                window.location.assign(window.PublicSite.pageUrl('/login'));
                throw new Error(window.PublicSite.t('Login is required.'));
            }
            const body = await window.PublicSite.responseMessage(response);
            if (!response.ok) throw new Error(body || window.PublicSite.t('The request could not be completed.'));
            return body ? JSON.parse(body) : null;
        };
        const openPaymentWindow = () => {
            const width = 520;
            const height = 720;
            const left = Math.max(0, window.screenX + (window.outerWidth - width) / 2);
            const top = Math.max(0, window.screenY + (window.outerHeight - height) / 2);
            const popup = window.open('', `registrationPayment-${Date.now()}`, `popup=yes,width=${width},height=${height},left=${left},top=${top},scrollbars=yes,resizable=yes`);
            if (!popup) return null;
            popup.document.title = window.PublicSite.t('Online Registration Payment');
            popup.document.body.replaceChildren();
            const message = popup.document.createElement('p');
            message.textContent = window.PublicSite.t('Preparing payment...');
            message.style.cssText = 'margin:40px;font:16px sans-serif;text-align:center;color:#334155';
            popup.document.body.append(message);
            return popup;
        };
        const loadScript = (source, popup) => new Promise((resolve, reject) => {
            const existing = Array.from(popup.document.scripts).find((script) => script.src === source);
            if (existing?.dataset.loaded === 'true') {
                resolve();
                return;
            }
            const script = existing || popup.document.createElement('script');
            script.src = source;
            script.async = true;
            script.addEventListener('load', () => {
                script.dataset.loaded = 'true';
                resolve();
            }, {once: true});
            script.addEventListener('error', () => reject(new Error(window.PublicSite.t('The payment module could not be loaded.'))), {once: true});
            if (!existing) popup.document.head.append(script);
        });
        // This callback only means the browser PG module returned. The server must verify and
        // persist the final payment result before the registration can be treated as paid.
        const startPayment = async (session, popup) => {
            if (session.provider !== 'paygate' || !popup || popup.closed) {
                throw new Error(window.PublicSite.t('The configured payment method is not supported.'));
            }
            const paymentForm = popup.document.createElement('form');
            const paymentScreen = popup.document.createElement('div');
            popup.document.body.replaceChildren(paymentForm, paymentScreen);
            paymentForm.append(...Object.entries(session.fields).map(([name, value]) => {
                const input = popup.document.createElement('input');
                input.type = 'hidden';
                input.name = name;
                input.value = value;
                return input;
            }));
            paymentForm.name = session.formName;
            paymentForm.id = session.formName;
            paymentForm.method = 'post';
            paymentScreen.id = session.screenElementId;
            await loadScript(session.scriptUrl, popup);
            if (typeof popup.doTransaction !== 'function') throw new Error(window.PublicSite.t('The payment module is not ready.'));
            await new Promise((resolve, reject) => {
                const previous = popup.getPGIOresult;
                const closedCheck = window.setInterval(() => {
                    if (!popup.closed) return;
                    window.clearInterval(closedCheck);
                    window.clearTimeout(timeout);
                    reject(new Error(window.PublicSite.t('The payment window was closed.')));
                }, 500);
                const timeout = window.setTimeout(() => {
                    window.clearInterval(closedCheck);
                    if (!popup.closed) popup.getPGIOresult = previous;
                    reject(new Error(window.PublicSite.t('The payment response timed out.')));
                }, 10 * 60 * 1000);
                popup.getPGIOresult = () => {
                    window.clearInterval(closedCheck);
                    window.clearTimeout(timeout);
                    popup.getPGIOresult = previous;
                    resolve();
                };
                try {
                    popup.doTransaction(paymentForm);
                } catch (error) {
                    window.clearInterval(closedCheck);
                    window.clearTimeout(timeout);
                    popup.getPGIOresult = previous;
                    reject(error);
                }
            });
        };

        categoryList?.addEventListener('change', refreshAmount);
        optionList?.addEventListener('input', refreshAmount);
        cancelButton?.addEventListener('click', () => {
            if (cancelDialog instanceof HTMLDialogElement) cancelDialog.showModal();
        });
        confirmCancelButton?.addEventListener('click', async (event) => {
            event.preventDefault();
            if (cancelDialog instanceof HTMLDialogElement) cancelDialog.close();
            if (cancelButton instanceof HTMLButtonElement) cancelButton.disabled = true;
            try {
                await requestJson(window.PublicSite.apiUrl('/api/public/pre-registrations/cancel'), {
                    method: 'POST', headers: {'Accept': 'application/json', ...csrfHeaders()}
                });
                window.alert(window.PublicSite.t('Your registration has been cancelled.'));
                window.location.reload();
            } catch (error) {
                const message = error instanceof Error ? error.message : window.PublicSite.t('The registration could not be cancelled.');
                setStatus(message, true);
                window.alert(message);
                if (cancelButton instanceof HTMLButtonElement) cancelButton.disabled = false;
            }
        });
        refundButton?.addEventListener('click', () => {
            if (!currentRegistration || !(refundDialog instanceof HTMLDialogElement)) return;
            renderRefundItems(currentRegistration);
            refundDialog.showModal();
        });
        refundItems?.addEventListener('change', (event) => {
            const changed = event.target;
            if (!(changed instanceof HTMLInputElement)) return;
            if (changed.matches('[data-refund-registration-fee]:checked')) {
                refundItems.querySelectorAll('[data-refund-amount]').forEach((input) => input.checked = true);
            } else if (!changed.checked) {
                const registrationFee = refundItems.querySelector('[data-refund-registration-fee]');
                if (registrationFee instanceof HTMLInputElement) registrationFee.checked = false;
            }
            refreshRefundTotal();
        });
        submitRefundButton?.addEventListener('click', () => {
            window.alert(window.PublicSite.t('Cancellation request submission will be connected in the next step.'));
        });

        requestJson(window.PublicSite.apiUrl('/api/public/pre-registrations/form'))
            .then((data) => data && renderForm(data))
            .catch((error) => setStatus(error instanceof Error ? error.message : window.PublicSite.t('Registration form could not be loaded.'), true));

        publicRegistrationForm.addEventListener('submit', async (event) => {
            event.preventDefault();
            setStatus('');
            const editableRegistration = currentRegistration?.paymentStatus === 'UNPAID';
            if ((!currentRegistration || editableRegistration) && !publicRegistrationForm.reportValidity()) return;
            const paymentRequired = currentRegistration && !editableRegistration
                ? Number(currentRegistration.totalAmount) > 0
                : Number(calculatedTotal()) > 0;
            const paymentPopup = paymentRequired ? openPaymentWindow() : null;
            if (paymentRequired && !paymentPopup) {
                const message = window.PublicSite.t('Please allow pop-ups to open the payment window.');
                setStatus(message, true);
                window.alert(message);
                return;
            }
            const originalText = submitButton?.textContent;
            if (submitButton instanceof HTMLButtonElement) {
                submitButton.disabled = true;
                submitButton.textContent = currentRegistration && !editableRegistration ? window.PublicSite.t('Opening Payment...') : window.PublicSite.t('Saving...');
            }
            if (cancelButton instanceof HTMLButtonElement) cancelButton.disabled = true;
            try {
                if (!currentRegistration) {
                    currentRegistration = await requestJson(publicRegistrationForm.action, {
                        method: 'POST',
                        headers: {'Accept': 'application/json', 'Content-Type': 'application/json', ...csrfHeaders()},
                        body: JSON.stringify({
                            categorySeq: Number(selectedCategoryInput()?.value),
                            options: selectedOptions(),
                            privacyAgreed: publicRegistrationForm.elements.namedItem('privacyAgreed')?.checked === true,
                            termsAgreed: publicRegistrationForm.elements.namedItem('termsAgreed')?.checked === true
                        })
                    });
                } else if (editableRegistration) {
                    currentRegistration = await requestJson(publicRegistrationForm.action, {
                        method: 'PUT',
                        headers: {'Accept': 'application/json', 'Content-Type': 'application/json', ...csrfHeaders()},
                        body: JSON.stringify({
                            categorySeq: Number(selectedCategoryInput()?.value),
                            options: selectedOptions()
                        })
                    });
                }
                if (!currentRegistration) {
                    paymentPopup?.close();
                    return;
                }
                if (currentRegistration?.paymentStatus === 'PAID') {
                    paymentPopup?.close();
                    window.alert(window.PublicSite.t('Registration {number} is complete.', {number: currentRegistration.registrationNumber}));
                    window.location.assign(window.PublicSite.pageUrl('/mypage-registration'));
                    return;
                }
                if (submitButton instanceof HTMLButtonElement) submitButton.textContent = window.PublicSite.t('Opening Payment...');
                const checkout = await requestJson(window.PublicSite.apiUrl('/api/public/pre-registrations/checkout'), {
                    method: 'POST', headers: {'Accept': 'application/json', ...csrfHeaders()}
                });
                await startPayment(checkout, paymentPopup);
                paymentPopup?.close();
                window.alert(window.PublicSite.t('The payment response was received. Payment status will be updated after server verification.'));
                window.location.assign(window.PublicSite.pageUrl('/mypage-registration'));
            } catch (error) {
                paymentPopup?.close();
                const message = error instanceof Error ? error.message : window.PublicSite.t('Online registration could not be completed.');
                setStatus(message, true);
                window.alert(message);
                if (submitButton instanceof HTMLButtonElement) {
                    submitButton.disabled = false;
                    submitButton.textContent = originalText || (currentRegistration ? window.PublicSite.t('Continue to Payment') : window.PublicSite.t('Proceed to Payment'));
                }
                if (cancelButton instanceof HTMLButtonElement && currentRegistration
                    && ['UNPAID', 'FAILED'].includes(currentRegistration.paymentStatus)) cancelButton.disabled = false;
            }
        });
    }
})();
