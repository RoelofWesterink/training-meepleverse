package io.fluxzero.training.meepleverse.catalog;

import io.fluxzero.sdk.Fluxzero;
import io.fluxzero.sdk.tracking.handling.authentication.NoUserRequired;
import io.fluxzero.sdk.web.HandleGet;
import io.fluxzero.sdk.web.Path;
import io.fluxzero.sdk.web.QueryParam;
import io.fluxzero.training.meepleverse.catalog.api.SearchItems;
import io.fluxzero.training.meepleverse.catalog.api.model.Item;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Path("/catalog")
@NoUserRequired
public class CatalogEndpoint {

    @HandleGet
    List<Item> getItems(@QueryParam String term) {
        return Fluxzero.queryAndWait(new SearchItems(term));
    }

}
