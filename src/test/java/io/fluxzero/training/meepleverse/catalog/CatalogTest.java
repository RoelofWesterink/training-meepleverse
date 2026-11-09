package io.fluxzero.training.meepleverse.catalog;

import io.fluxzero.sdk.test.TestFixture;
import io.fluxzero.training.meepleverse.catalog.api.SearchItems;
import io.fluxzero.training.meepleverse.catalog.api.model.Item;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CatalogTest {

    final TestFixture fixture = TestFixture.create(CatalogEndpoint.class, CatalogHandler.class);

    @Test
    void registerAndSearchItems() {
        fixture.givenCommands("/catalog/register-items.json")
                .whenQuery(new SearchItems())
                .expectResult(page -> page.items().size() == 35);
    }

    @Test
    void catalogEndpointReturnsAListOfTitleMatches() {
        fixture.givenCommands("/catalog/register-items.json")
                .whenGet("/catalog?term=AndEM")
                .expectWebResult(response -> {
                    Item[] matches = response.getPayloadAs(Item[].class);
                    assertEquals(1, matches.length);
                    assertEquals("pandemic", matches[0].itemId().toString());
                    return true;
                });
    }

    @Test
    void categorySelectionOnlyNarrowsTheGames() {
        fixture.givenCommands("/catalog/register-items.json")
                .whenQuery(new SearchItems(null, "cooperative"))
                .expectResult(page -> {
                    assertEquals(Map.of("abstract", 2, "cooperative", 3, "deckbuilding", 2,
                            "family", 11, "party", 5, "strategy", 12), page.categories());
                    assertEquals(Set.of("pandemic", "spirit-island", "the-crew"), page.items().stream()
                            .map(i -> i.itemId().toString()).collect(Collectors.toSet()));
                    return true;
                });
    }

    @Test
    void countsFollowTheTitleEvenWhenTheSelectedCategoryHasNoMatches() {
        fixture.givenCommands("/catalog/register-items.json")
                .whenGet("/catalog/browse?term=AndEM&category=strategy")
                .expectWebResult(response -> {
                    SearchItems.Result result = response.getPayloadAs(SearchItems.Result.class);
                    assertEquals(Map.of("cooperative", 1), result.categories());
                    assertTrue(result.items().isEmpty());
                    return true;
                });
    }
}
