# Exercise 6 · Index the game categories

The shop already works well with 35 games. This is a deliberately small example for learning how Fluxzero indexes category counts and how you can update existing documents. Your work is in the Java backend. The running shop and its existing tests are your acceptance checks.

1. Start the shop and open the catalogue. Choose **Cooperative (3)**, search for **Pandemic**, and then clear the search. Note the games and counts. In Devboard, open **Documents → Item → pandemic**. Compare the category in the JSON with the indexed facets.
2. Read `SearchItems`. The category counts cover all titles matching the search text. Selecting a category narrows the games, while the other category counts are still available. Replace the in-memory counting and category filtering with `facetStats()` and `matchFacet(...)`. Inspect the annotations on both `Item` and `ItemDetails`.
3. Keep the same Dev Server running as you change the model. The existing items were indexed before your annotation change. Use the document revision and a dedicated `@HandleDocument` consumer to rewrite those stored items. Return the item at the new revision so the SDK can persist it under the existing ID. Let the consumer catch up with documents already in the collection.
4. Inspect the same document again. Check its ID, release date and category facet. Repeat the shop checks. Also try selecting **Strategy** before searching for **Pandemic**: no games match that combination, but **Cooperative (1)** is still available. Switch to it to find Pandemic.

Use `CatalogTest` while working. During the debrief, explain what caused the existing document to be indexed again and why receiving its updated version does not cause another rewrite.
