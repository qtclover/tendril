package io.github.qtclover.tendril;

import io.github.qtclover.tendril.fixtures.Account;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Hooks on field reads and writes inside a method. */
class FieldTest {

    private final Account account = new Account("Ada");

    @AfterEach
    void cleanUp() {
        Injector.removeAll();
    }

    @Test
    void aReadCanBeObservedAndReplaced() {
        List<Object> seen = new ArrayList<>();
        Injector.into(Account.class).method("balance").onFieldRead("balance", call -> {
            assertEquals(At.FIELD_READ, call.at());
            InvokedCall read = call.invoked();
            assertEquals("balance", read.name());
            assertEquals("io.github.qtclover.tendril.fixtures.Account", read.owner());
            assertEquals("()J", read.descriptor());
            assertSame(account, read.receiver());
            assertEquals(0, read.argCount());
            seen.add(read.getResult());
            read.setResult(999);        // an Integer for a long field
        });
        account.deposit(50);

        assertEquals(999L, account.balance());
        assertEquals(List.of(50L), seen);
        assertEquals("Ada has 50", account.describe(), "only reads inside balance() are hooked");
    }

    @Test
    void aWriteCanBeObservedChangedAndSkipped() {
        List<Object> written = new ArrayList<>();
        Injection change = Injector.into(Account.class).method("deposit").onFieldWrite("Account.balance", call -> {
            assertEquals(At.FIELD_WRITE, call.at());
            assertEquals("(J)V", call.invoked().descriptor());
            assertSame(account, call.invoked().receiver());
            written.add(call.invoked().arg(0));
            call.invoked().setArg(0, call.invoked().<Long>arg(0) * 2);
        });
        account.deposit(50);
        assertEquals(100L, account.balance());
        assertEquals(List.of(50L), written);
        change.remove();

        Injector.into(Account.class).method("deposit").onFieldWrite("balance", call -> call.invoked().skip());
        account.deposit(1000);
        assertEquals(100L, account.balance());
    }

    @Test
    void referenceFieldsAndBothDirectionsInOneMethod() {
        Injector.into(Account.class).method("describe")
                .onFieldRead("owner", call -> call.invoked().setResult("Somebody"));
        Injector.into(Account.class).method("describe")
                .onFieldRead("balance", call -> call.invoked().setResult(-1L));
        Injector.into(Account.class).method("rename")
                .onFieldWrite("owner", call -> call.invoked().setArg(0, call.invoked().arg(0) + "!"));

        assertEquals("Somebody has -1", account.describe());
        account.rename("Grace");
        Injector.removeAll();
        assertEquals("Grace! has 0", account.describe());
    }

    @Test
    void staticFields() {
        Account.reset();
        Injector.into(Account.class).method("count").onFieldRead("opened", call -> {
            assertNull(call.invoked().receiver());
            call.invoked().setResult(call.invoked().<Integer>getResultAs() + 100);
        });
        Injector.into(Account.class).method("reset").onFieldWrite("opened", call -> call.invoked().setArg(0, 7));

        assertEquals(100, Account.count());
        Account.reset();
        assertEquals(107, Account.count());
    }

    @Test
    void fieldWritesInAConstructorAfterSuper() {
        Injector.into(Account.class).constructor()
                .onFieldWrite("owner", call -> call.invoked().setArg(0, "Hooked"));
        Injector.into(Account.class).constructor()
                .onFieldWrite("id", call -> call.invoked().setArg(0, -5));     // a final field

        Account hooked = new Account("Plain");
        assertEquals("Hooked has 0", hooked.describe());
        assertEquals(-5, hooked.id());
    }

    @Test
    void annotationsAndUndo() throws Exception {
        byte[] original = TestSupport.currentBytes(Account.class);
        Injection hooks = Injector.register(Hooks.class);
        account.deposit(10);
        assertEquals(1L, account.balance());

        hooks.remove();
        assertEquals(10L, account.balance());
        assertArrayEquals(original, TestSupport.currentBytes(Account.class));
    }

    static class Hooks {
        @Inject(target = Account.class, method = "balance", at = At.FIELD_READ, invoke = "balance")
        static void one(CallInfo call) {
            call.invoked().setResult(1L);
        }
    }

    @Test
    void misuseAndThingsThatAreNotFieldAccesses() {
        // A compile-time constant is copied into the method; no field is read.
        InjectionException constant = assertThrows(InjectionException.class,
                () -> Injector.into(Account.class).method("limit").onFieldRead("LIMIT", call -> { }));
        assertTrue(constant.getMessage().contains("No call matching"), constant.getMessage());
        // balance() reads the field but never writes it.
        assertThrows(InjectionException.class,
                () -> Injector.into(Account.class).method("balance").onFieldWrite("balance", call -> { }));
        assertThrows(InjectionException.class,
                () -> Injector.into(Account.class).method("balance").onFieldRead("balance()J", call -> { }));

        List<Throwable> errors = new ArrayList<>();
        Injector.into(Account.class).method("balance").onFieldRead("balance", call -> {
            errors.add(assertThrows(IllegalStateException.class, () -> call.invoked().skip()));
            errors.add(assertThrows(IllegalArgumentException.class, () -> call.invoked().setResult("text")));
        });
        Injector.into(Account.class).method("deposit").onFieldWrite("balance", call ->
                errors.add(assertThrows(IllegalStateException.class, () -> call.invoked().setResult(1L))));
        account.deposit(1);
        account.balance();
        assertEquals(3, errors.size());
    }
}
