package com.foundation.org.infrastructure.person;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** F-03 BAS-02 §4.2：手机 / 邮箱脱敏（tail-4 / domain）。 */
class PersonMaskerTest {

    private final PersonMasker masker = new PersonMasker();

    @Test
    void mobileKeepsTailFourDigits() {
        assertThat(masker.maskMobile("13800138000")).isEqualTo("*******8000");
    }

    @Test
    void shortOrBlankMobileReturnedAsIs() {
        assertThat(masker.maskMobile(null)).isNull();
        assertThat(masker.maskMobile("")).isEmpty();
        assertThat(masker.maskMobile("1234")).isEqualTo("1234");
    }

    @Test
    void emailKeepsFirstCharAndDomain() {
        assertThat(masker.maskEmail("alice@example.com")).isEqualTo("a****@example.com");
    }

    @Test
    void singleCharLocalPartKeepsOneMask() {
        assertThat(masker.maskEmail("a@example.com")).isEqualTo("a*@example.com");
    }

    @Test
    void invalidOrBlankEmailReturnedAsIs() {
        assertThat(masker.maskEmail(null)).isNull();
        assertThat(masker.maskEmail("")).isEmpty();
        assertThat(masker.maskEmail("no-at-sign")).isEqualTo("no-at-sign");
        assertThat(masker.maskEmail("@example.com")).isEqualTo("@example.com");
    }

    @Test
    void defaultModesAreTail4AndDomain() {
        assertThat(masker.mobileMode()).isEqualTo(PersonMasker.MOBILE_TAIL_4);
        assertThat(masker.emailMode()).isEqualTo(PersonMasker.EMAIL_DOMAIN);
    }

    @Test
    void blankModeFallsBackToDefault() {
        PersonMasker custom = new PersonMasker(" ", null);

        assertThat(custom.mobileMode()).isEqualTo(PersonMasker.MOBILE_TAIL_4);
        assertThat(custom.emailMode()).isEqualTo(PersonMasker.EMAIL_DOMAIN);
    }
}
