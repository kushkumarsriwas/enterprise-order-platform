package com.enterprise.inventory.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.enterprise.inventory.catalog.CatalogDtos.ProductResponse;
import com.enterprise.inventory.catalog.StorefrontDtos.FacetValue;
import com.enterprise.inventory.catalog.StorefrontDtos.SearchCriteria;
import com.enterprise.inventory.catalog.StorefrontDtos.SearchFacets;
import com.enterprise.inventory.common.ApiException;
import com.enterprise.inventory.common.PageResponse;
import com.enterprise.inventory.stock.Inventory;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

/** Search, filters, facets and relevance of the marketplace catalogue against a real (H2) database. */
@DataJpaTest(properties = "spring.datasource.url=jdbc:h2:mem:productsearch;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ProductService.class, CategoryService.class, ProductCache.class})
class ProductServiceTest {

    @Autowired
    private ProductService productService;
    @Autowired
    private EntityManager em;

    private Category electronics;
    private Category home;

    @BeforeEach
    void setUp() {
        electronics = persist(new Category("Electronics", null, "smartphone"));
        home = persist(new Category("Home & Kitchen", null, "sofa"));
        product(1L, "Gulf Gadgets", "PH-1", "Galaxy Phone 128GB", "Samsung", electronics, "2999.00", "3499.00", 10, true);
        product(1L, "Gulf Gadgets", "PH-2", "Phone case for Galaxy", "Spigen", electronics, "79.00", null, 0, true);
        product(2L, "Desert Deals", "PH-3", "Smart phone stand", "Ugreen", electronics, "49.00", null, 5, true);
        product(2L, "Desert Deals", "KT-1", "Electric kettle 1.7L", "Philips", home, "149.00", "199.00", 8, true);
        product(2L, "Desert Deals", "PH-4", "Phone - inventory only", "Samsung", electronics, "999.00", null, 3, false);
        em.flush();
    }

    private <T> T persist(T entity) {
        em.persist(entity);
        return entity;
    }

    private void product(long sellerId, String store, String sku, String name, String brand, Category category,
                         String price, String wasPrice, int available, boolean listed) {
        Product product = new Product(sellerId, store, sku, name, new BigDecimal(price), new BigDecimal("1.00"));
        product.describe(name, brand, null, "Description of " + name, Unit.PIECE, category, null);
        product.price(new BigDecimal(price), wasPrice == null ? null : new BigDecimal(wasPrice), new BigDecimal("1.00"));
        product.setListed(listed);
        em.persist(product);
        Inventory inventory = new Inventory(product);
        ReflectionTestUtils.setField(inventory, "availableQuantity", available);
        em.persist(inventory);
    }

    private PageResponse<ProductResponse> search(String q, Long categoryId, List<Long> sellers, List<String> brands,
                                                 boolean inStock, boolean deals, String sort) {
        return productService.search(new SearchCriteria(q, categoryId, sellers, brands, null, null, null, inStock, deals,
                sort), 0, 24);
    }

    @Test
    void everyWordMustMatchAndInventoryOnlyProductsNeverAppear() {
        PageResponse<ProductResponse> phones = search("phone", null, null, null, false, false, null);
        assertThat(phones.content()).extracting(ProductResponse::sku).containsExactlyInAnyOrder("PH-1", "PH-2", "PH-3");

        assertThat(search("galaxy case", null, null, null, false, false, null).content())
                .extracting(ProductResponse::sku).containsExactly("PH-2");
    }

    @Test
    void relevancePutsNameMatchesFirst() {
        // "Galaxy Phone" starts with the phrase, "Phone case for Galaxy" only contains a word of it
        assertThat(search("galaxy", null, null, null, false, false, "relevance").content())
                .extracting(ProductResponse::sku).containsExactly("PH-1", "PH-2");
    }

    @Test
    void filtersCombine() {
        assertThat(search("phone", null, List.of(2L), null, false, false, null).content())
                .extracting(ProductResponse::sku).containsExactly("PH-3");
        assertThat(search(null, null, null, List.of("samsung"), false, false, null).content())
                .extracting(ProductResponse::sku).containsExactly("PH-1");
        assertThat(search("phone", null, null, null, true, false, "price_asc").content())
                .extracting(ProductResponse::sku).containsExactly("PH-3", "PH-1");
        assertThat(search(null, null, null, null, false, true, "discount").content())
                .extracting(ProductResponse::sku).containsExactly("PH-1", "KT-1");
        assertThat(search(null, home.getId(), null, null, false, false, null).content())
                .extracting(ProductResponse::sku).containsExactly("KT-1");
    }

    @Test
    void wildcardCharactersAreSearchedLiterally() {
        assertThat(search("%", null, null, null, false, false, null).content()).isEmpty();
        assertThat(search("_", null, null, null, false, false, null).content()).isEmpty();
    }

    @Test
    void facetsCountTheResultsOfTheSearch() {
        SearchFacets facets = productService.facets(new SearchCriteria("phone", null, null, null, null, null, null,
                false, false, null));

        assertThat(facets.total()).isEqualTo(3);
        assertThat(facets.categories()).extracting(FacetValue::label).containsExactly("Electronics");
        assertThat(facets.sellers()).extracting(FacetValue::label, FacetValue::count)
                .containsExactlyInAnyOrder(org.assertj.core.groups.Tuple.tuple("Gulf Gadgets", 2L),
                        org.assertj.core.groups.Tuple.tuple("Desert Deals", 1L));
        assertThat(facets.minPrice()).isEqualByComparingTo("49.00");
        assertThat(facets.maxPrice()).isEqualByComparingTo("2999.00");
        assertThat(facets.inStock()).isEqualTo(2);
        assertThat(facets.deals()).isEqualTo(1);
    }

    @Test
    void publicViewHidesCostAndShowsTheDiscount() {
        ProductResponse phone = search("galaxy phone 128gb", null, null, null, false, false, null).content().get(0);
        assertThat(phone.discountPercent()).isEqualTo(14);
        assertThat(phone.compareAtPrice()).isEqualByComparingTo("3499.00");
        assertThat(phone.inStock()).isTrue();
        assertThat(phone.fewLeft()).isFalse();
    }

    @Test
    void homeSectionsAndSuggestions() {
        var home = productService.home();
        assertThat(home.deals()).extracting(ProductResponse::sku).containsExactly("KT-1", "PH-1");
        assertThat(home.newArrivals()).hasSize(4);
        assertThat(home.bestSellers()).isEmpty();
        assertThat(productService.suggest("gal").products()).contains("Galaxy Phone 128GB");
    }

    @Test
    void unlistedProductDetailsAreNotFound() {
        Long hidden = em.createQuery("select p.id from Product p where p.sku = 'PH-4'", Long.class).getSingleResult();
        assertThatThrownBy(() -> productService.getById(hidden)).isInstanceOf(ApiException.class);
    }
}
