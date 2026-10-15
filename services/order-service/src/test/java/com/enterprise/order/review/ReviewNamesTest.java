package com.enterprise.order.review;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Public reviews show "first name + initial", never the customer's full name. */
class ReviewNamesTest {

    @Test
    void showsFirstNameAndTheInitialOfTheLastName() {
        assertThat(ReviewService.publicName("Aisha Al Mansoori", "aisha")).isEqualTo("Aisha M.");
        assertThat(ReviewService.publicName("  rahul   menon ", "rahul")).isEqualTo("rahul M.");
    }

    @Test
    void keepsASingleNameAsItIs() {
        assertThat(ReviewService.publicName("Hamad", "hamad01")).isEqualTo("Hamad");
    }

    @Test
    void fallsBackToTheUsernameWithoutADeliveryName() {
        assertThat(ReviewService.publicName(null, "maria")).isEqualTo("maria");
        assertThat(ReviewService.publicName("   ", "maria")).isEqualTo("maria");
    }
}
