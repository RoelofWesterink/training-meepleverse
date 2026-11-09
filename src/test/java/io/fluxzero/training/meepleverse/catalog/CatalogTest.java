package io.fluxzero.training.meepleverse.catalog;

import io.fluxzero.sdk.test.TestFixture;
import io.fluxzero.training.meepleverse.catalog.api.SearchItems;
import org.junit.jupiter.api.Test;

class CatalogTest {

    final TestFixture fixture = TestFixture.create(CatalogEndpoint.class, CatalogHandler.class);

    @Test
    void registerAndSearchItems() {
        fixture.givenCommands("/catalog/register-items.json")
                .whenQuery(new SearchItems())
                .expectResult(r -> r.size() > 1);
    }
}