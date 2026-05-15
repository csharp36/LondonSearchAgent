package com.londonsearch.repository;

import com.londonsearch.model.Property;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class PropertyRepositoryTest {

    @Autowired
    private PropertyRepository propertyRepository;

    private Property testProperty;

    @BeforeEach
    void setUp() {
        testProperty = new Property();
        testProperty.setId("test-prop-" + System.nanoTime());
        testProperty.setAddress("42 Baker Street, London W1U 3BW");
        testProperty.setNormalizedAddress("42 baker street, london w1u 3bw");
        testProperty.setArea("Marylebone");
        testProperty.setBedrooms(3);
        testProperty.setBathrooms(2);
        testProperty.setPrice(7500);
        testProperty.setCurrency("GBP");
        testProperty.setPricePerMonth(7500);
        testProperty.setSqft(1200);
        testProperty.setPropertyType("Flat");
        testProperty.setFurnishing("Furnished");
        testProperty.setStatus("new");
        testProperty.setMatchScore(92);
        testProperty.setFirstSeenAt(Instant.now());
        testProperty.setLastUpdatedAt(Instant.now());
    }

    @Test
    void saveAndFindById() {
        propertyRepository.save(testProperty);
        Optional<Property> found = propertyRepository.findById(testProperty.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getAddress()).isEqualTo("42 Baker Street, London W1U 3BW");
        assertThat(found.get().getBedrooms()).isEqualTo(3);
    }

    @Test
    void findByIdReturnsEmptyForMissing() {
        Optional<Property> found = propertyRepository.findById("nonexistent-" + System.nanoTime());
        assertThat(found).isEmpty();
    }

    @Test
    void findByArea() {
        propertyRepository.save(testProperty);
        List<Property> marylebone = propertyRepository.findByArea("Marylebone");
        assertThat(marylebone).isNotEmpty();
        assertThat(marylebone.get(0).getArea()).isEqualTo("Marylebone");
    }

    @Test
    void findByStatus() {
        propertyRepository.save(testProperty);
        List<Property> newProps = propertyRepository.findByStatus("new");
        assertThat(newProps).isNotEmpty();
        assertThat(newProps.get(0).getStatus()).isEqualTo("new");
    }
}
