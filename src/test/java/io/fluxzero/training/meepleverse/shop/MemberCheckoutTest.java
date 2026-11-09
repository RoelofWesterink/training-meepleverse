package io.fluxzero.training.meepleverse.shop;

import io.fluxzero.sdk.test.TestFixture;
import io.fluxzero.sdk.Fluxzero;
import io.fluxzero.training.meepleverse.catalog.CatalogHandler;
import io.fluxzero.sdk.web.WebRequest;
import io.fluxzero.training.meepleverse.catalog.api.model.ItemId;
import io.fluxzero.training.meepleverse.orders.OrdersEndpoint;
import io.fluxzero.training.meepleverse.orders.api.PlaceOrder;
import io.fluxzero.training.meepleverse.orders.api.model.*;
import io.fluxzero.training.meepleverse.payments.api.model.PaymentId;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MemberCheckoutTest {
    final ShopLogin login = mock(ShopLogin.class);
    final ShopLogin.Member robin = new ShopLogin.Member(ShopLogin.accountKey("issuer", "Robin"), "Robin", "csrf");
    final ShopLogin.Member sam = new ShopLogin.Member(ShopLogin.accountKey("issuer", "Sam"), "Sam", "csrf-2");
    final Addressee address = new Addressee("Robin", "Game Lane", "42", "1234 AB", "Meepleville",
            "Netherlands", "robin@example.test", null);
    final OrderDetails details = new OrderDetails(List.of(new ItemId("wingspan")), new PaymentId("payment"), address);
    final TestFixture fixture = TestFixture.create(new MemberCheckout(login), new OrdersEndpoint(), new CatalogHandler());

    @Test void guessingAnotherMembersReceiptWorksOnNeitherEndpoint() {
        when(login.member(any())).thenReturn(sam);
        fixture.givenCommands(new PlaceOrder(new OrderId("robins-order"), details, robin.key()))
                .whenGet("/shop-account/orders/robins-order").expectWebResult(r -> r.getStatus() == 404)
                .andThen().whenGet("/orders/robins-order").expectWebResult(r -> r.getStatus() == 404)
                .andThen().whenGet("/shop-account/orders").expectWebResult(r -> {
                    assertTrue(r.<List<?>>getPayloadAs(List.class).isEmpty()); return true;
                });
    }

    @Test void onlyAnAuthorizedCheckoutCanStartAPayment() {
        when(login.member(any())).thenReturn(robin); // Valid session, missing CSRF proof.
        fixture.whenPost("/shop-account/orders", new MemberCheckout.Purchase(List.of(new ItemId("wingspan"))))
                .expectWebResult(r -> r.getStatus() == 401).expectNoCommands();
    }

    @Test void changingAddressPreservesThePaymentAccountAndDoesNotChangeAnotherProfile() {
        when(login.member(any())).thenReturn(robin);
        when(login.writable(any())).thenReturn(robin);
        fixture.whenGet("/shop-account/profile").expectWebResult(r -> {
            MemberCheckout.Account a = r.getPayloadAs(MemberCheckout.Account.class);
            assertNotNull(a.paymentMethod());
            assertNull(Fluxzero.get().keyValueStore().get("shop-profile:" + robin.key()));
            return true;
        }).andThen().whenPost("/shop-account/delivery", address).expectWebResult(r -> r.getStatus() == 200)
          .andThen().whenGet("/shop-account/profile").expectWebResult(r -> {
            MemberCheckout.Account a = r.getPayloadAs(MemberCheckout.Account.class);
            assertEquals(address, a.delivery());
            assertTrue(a.paymentMethod().contains(robin.key().substring(0, 10))); return true;
        });
        when(login.member(any())).thenReturn(sam);
        fixture.whenGet("/shop-account/profile").expectWebResult(r -> {
            MemberCheckout.Account a = r.getPayloadAs(MemberCheckout.Account.class);
            assertEquals("Sam", a.delivery().fullName());
            assertFalse(a.paymentMethod().contains(robin.key().substring(0, 10))); return true;
        });
    }

    @Test void twoClicksCreateDistinctOrdersAndPaymentReferencesForOneWallet() {
        when(login.writable(any())).thenReturn(robin);
        var purchase = new MemberCheckout.Purchase(List.of(new ItemId("wingspan")));
        var ids = new java.util.HashSet<OrderId>();
        var charges = new java.util.HashSet<PaymentId>();
        io.fluxzero.common.ThrowingPredicate<io.fluxzero.sdk.web.WebResponse> receipt = r -> {
            OrdersEndpoint.OrderReceipt received = r.getPayloadAs(OrdersEndpoint.OrderReceipt.class);
            var order = Fluxzero.loadAggregate(received.orderId()).get();
            assertEquals(robin.key(), order.customerId());
            assertEquals(purchase.itemIds(), order.details().itemIds());
            assertTrue(order.details().paymentId().toString().startsWith("MP-" + robin.key().substring(0, 10)));
            assertTrue(ids.add(order.orderId()));
            assertTrue(charges.add(order.details().paymentId()));
            return true;
        };
        fixture.givenCommands("/catalog/register-items.json")
                .whenPost("/shop-account/orders", purchase).expectWebResult(receipt)
                .andThen().whenPost("/shop-account/orders", purchase).expectWebResult(receipt);
    }

    @Test void inventedSessionOrInvalidCallbackDoesNotSignIn() {
        var sessions = new ShopLogin();
        assertNull(sessions.member(WebRequest.get("/shop-account/profile")
                .header("Cookie", "meeple_session=invented").build()));
        assertEquals("/shop/#/login-error", sessions.callback(WebRequest.get("/app/callback?state=wrong&code=fake")
                .build()).getHeader("Location"));
    }
}
