import io.github.palette4j.util.CompensationScope;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class CompensationScopeTest {
    @DisplayName("close() --- commit() has been called prior to close()")
    @Test
    void close_whenCommitted_doesNothing_andDoesNotRunDeferredActions() {
        AtomicInteger calls = new AtomicInteger();

        CompensationScope scope = new CompensationScope();
        scope.defer(calls::incrementAndGet);
        scope.defer(calls::incrementAndGet);

        scope.commit();
        scope.close();

        assertEquals(0, calls.get(), "No compensation actions must run after commit()");
    }

    @DisplayName("close() --- roll back deferred actions in LIFO order if commit() has not been called")
    @Test
    void close_whenNotCommitted_runsDeferredActions_inLifoOrder() {
        List<Integer> order = new ArrayList<>();

        CompensationScope scope = new CompensationScope();
        scope.defer(() -> order.add(1));
        scope.defer(() -> order.add(2));
        scope.defer(() -> order.add(3));

        scope.close();

        assertEquals(Arrays.asList(3, 2, 1), order, "Compensation must run in LIFO order");
    }

    @DisplayName("close() --- after close() the compensation scope is empty")
    @Test
    void close_isIdempotent_afterFirstCloseStackIsEmpty() {
        AtomicInteger calls = new AtomicInteger();

        CompensationScope scope = new CompensationScope();
        scope.defer(calls::incrementAndGet);
        scope.defer(calls::incrementAndGet);

        scope.close();
        scope.close();
        scope.close();

        assertEquals(2, calls.get(), "Deferred actions must run at most once");
    }

    @DisplayName("defer() without exception handler --- swallows exception and continues executing others ")
    @Test
    void defer_withoutExceptionHandler_swallowsExceptions_andContinuesExecutingOthers() {
        AtomicInteger calls = new AtomicInteger();

        CompensationScope scope = new CompensationScope();
        scope.defer(() -> {
            throw new RuntimeException("boom");
        });
        scope.defer(calls::incrementAndGet);

        assertDoesNotThrow(scope::close, "close() must not throw when using defer(Runnable)");
        assertEquals(1, calls.get(), "Later actions must still run even if earlier one throws");
    }

    @DisplayName("defer() with exception handler --- invokes handler and continues executing others")
    @Test
    void defer_withExceptionHandler_invokesHandler_andContinuesExecutingOthers() {
        AtomicInteger calls = new AtomicInteger();
        List<Throwable> seen = new ArrayList<>();

        Consumer<Throwable> handler = seen::add;

        CompensationScope scope = new CompensationScope();
        scope.defer(() -> {
            throw new IllegalStateException("boom");
        }, handler);
        scope.defer(calls::incrementAndGet);

        assertDoesNotThrow(scope::close);
        assertEquals(1, calls.get(), "Other actions must still run");
        assertEquals(1, seen.size(), "Exception handler must be invoked exactly once");
        assertInstanceOf(IllegalStateException.class, seen.get(0));
        assertEquals("boom", seen.get(0).getMessage());
    }

    @DisplayName("close() with exception handler --- stops further rollback execution if exception handler itself throws exception")
    @Test
    void close_whenExceptionHandlerItselfThrows_propagatesAndStopsFurtherExecution() {
        List<Integer> order = new ArrayList<>();

        CompensationScope scope = new CompensationScope();
        scope.defer(() -> order.add(1));

        scope.defer(
                () -> {
                    throw new RuntimeException("undo failed");
                },
                t -> {
                    throw new RuntimeException("handler failed");
                }
        );

        RuntimeException ex = assertThrows(RuntimeException.class, scope::close);
        assertEquals("handler failed", ex.getMessage());

        assertEquals(Collections.emptyList(), order, "Further actions must not run if close() is interrupted by an exception");
    }

    @DisplayName("commit() --- does not empty compensation scope after calling commit()")
    @Test
    void commit_doesNotClearStackButPreventsExecutionOnClose() {
        AtomicInteger calls = new AtomicInteger();

        CompensationScope scope = new CompensationScope();
        scope.defer(calls::incrementAndGet);

        scope.commit();
        scope.close();

        assertEquals(0, calls.get(), "commit() must suppress compensation execution");
    }

    @DisplayName("close() on try-with-resources --- runs compensation on exit")
    @Test
    void tryWithResources_runsCompensationOnExit_whenNotCommitted() {
        AtomicInteger calls = new AtomicInteger();

        try (CompensationScope scope = new CompensationScope()) {
            scope.defer(calls::incrementAndGet);
            scope.defer(calls::incrementAndGet);
        }

        assertEquals(2, calls.get(), "try-with-resources must trigger close() and execute compensation");
    }

    @DisplayName("close() on try-with-resources --- does not run compensation on exit if commit() has been called")
    @Test
    void tryWithResources_doesNotRunCompensationOnExit_whenCommitted() {
        AtomicInteger calls = new AtomicInteger();

        try (CompensationScope scope = new CompensationScope()) {
            scope.defer(calls::incrementAndGet);
            scope.commit();
        }

        assertEquals(0, calls.get(), "commit() must suppress compensation on try-with-resources exit");
    }
}
