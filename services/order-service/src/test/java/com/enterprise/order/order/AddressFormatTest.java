package com.enterprise.order.order;

import static org.assertj.core.api.Assertions.assertThat;

import com.enterprise.order.order.OrderDtos.AddressRequest;
import org.junit.jupiter.api.Test;

/** The delivery address is stored on the order as one line, the way it is printed on the tax invoice. */
class AddressFormatTest {

    @Test
    void leavesOutTheEmirateWhenItIsAlsoTheCity() {
        AddressRequest address = new AddressRequest("Aisha Al Mansoori", "+971 50 214 8833", "Marina Gate 2, Dubai Marina",
                "Apartment 1804", "Dubai", "Dubai", null);

        assertThat(address.formatted()).isEqualTo(
                "Aisha Al Mansoori, Marina Gate 2, Dubai Marina, Apartment 1804, Dubai, United Arab Emirates (+971 50 214 8833)");
    }

    @Test
    void keepsTheEmirateWhenItDiffersFromTheCityAndSkipsEmptyLines() {
        AddressRequest address = new AddressRequest(" Omar Haddad ", "+971 56 330 7781", "Sheikh Khalifa Street", "  ",
                "Al Nuaimiya", "Ajman", "United Arab Emirates");

        assertThat(address.formatted()).isEqualTo(
                "Omar Haddad, Sheikh Khalifa Street, Al Nuaimiya, Ajman, United Arab Emirates (+971 56 330 7781)");
    }
}
