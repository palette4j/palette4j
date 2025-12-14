import io.github.palette4j.state.GenericStateMachine;
import io.github.palette4j.state.Transition;
import io.github.palette4j.util.CompensationScope;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class GenericStateMachineTest {

    enum State { NEW, VALIDATED, FAILED, DONE }
    enum Trigger { VALIDATE, COMPLETE }

    static final class Ctx {
        private State state;

        Ctx(State state) { this.state = state; }

        State getState() { return state; }
        void setState(State state) { this.state = state; }
    }

    @DisplayName("submit() --- no transitions found, return unchanged context")
    @Test
    void submit_noTransitionFound_returnsContext_unchanged_andDoesNotCallTransition() {
        Ctx ctx = new Ctx(State.NEW);
        RecordingTransition transition = new RecordingTransition(State.NEW, Trigger.VALIDATE, State.VALIDATED);

        GenericStateMachine<State, Trigger, Ctx> sm =
                new GenericStateMachine<>(Ctx::getState, Ctx::setState, transition);

        Ctx result = sm.submit(ctx, Trigger.COMPLETE);

        assertSame(ctx, result);
        assertEquals(State.NEW, ctx.getState());

        assertEquals(0, transition.beforeCalls.get(), "beforeExecution must not be called when transition is not found");
        assertEquals(0, transition.executeCalls.get(), "execute must not be called when transition is not found");
        assertEquals(0, transition.afterCalls.get(), "afterExecution must not be called when transition is not found");
    }

    @DisplayName("submit() --- abort further transition as beforeExecution returns false")
    @Test
    void submit_beforeExecutionReturnsFalse_abortsTransition() {
        Ctx ctx = new Ctx(State.NEW);

        RecordingTransition transition = new RecordingTransition(State.NEW, Trigger.VALIDATE, State.VALIDATED)
                .withBeforeResult(false);

        GenericStateMachine<State, Trigger, Ctx> sm =
                new GenericStateMachine<>(Ctx::getState, Ctx::setState, transition);

        sm.submit(ctx, Trigger.VALIDATE);

        assertEquals(State.NEW, ctx.getState(), "state must remain unchanged when beforeExecution aborts");
        assertEquals(1, transition.beforeCalls.get());
        assertEquals(0, transition.executeCalls.get(), "execute must not be called when beforeExecution returns false");
        assertEquals(0, transition.afterCalls.get(), "afterExecution must not be called when aborted early");
    }

    @DisplayName("submit() --- updates context state correctly on success and calls hooks")
    @Test
    void submit_successOutcome_updatesStateToTransitionTo_andCallsHooks() {
        Ctx ctx = new Ctx(State.NEW);

        RecordingTransition transition = new RecordingTransition(State.NEW, Trigger.VALIDATE, State.VALIDATED)
                .withOutcomeSuccess();

        GenericStateMachine<State, Trigger, Ctx> sm =
                new GenericStateMachine<>(Ctx::getState, Ctx::setState, transition);

        sm.submit(ctx, Trigger.VALIDATE);

        assertEquals(State.VALIDATED, ctx.getState(), "on success outcome, state must move to transition.to");
        assertEquals(1, transition.beforeCalls.get());
        assertEquals(1, transition.executeCalls.get());
        assertEquals(1, transition.afterCalls.get());
    }

    @DisplayName("submit() --- updates context state correctly on failure and calls hooks")
    @Test
    void submit_failureOutcome_updatesStateToFailState_andCallsHooks() {
        Ctx ctx = new Ctx(State.NEW);

        RecordingTransition transition = new RecordingTransition(State.NEW, Trigger.VALIDATE, State.VALIDATED)
                .withOutcomeFailure(State.FAILED);

        GenericStateMachine<State, Trigger, Ctx> sm =
                new GenericStateMachine<>(Ctx::getState, Ctx::setState, transition);

        sm.submit(ctx, Trigger.VALIDATE);

        assertEquals(State.FAILED, ctx.getState(), "on failure outcome, state must move to outcome.failState");
        assertEquals(1, transition.beforeCalls.get());
        assertEquals(1, transition.executeCalls.get());
        assertEquals(1, transition.afterCalls.get());
    }

    @DisplayName("submit() with external compensation scope --- does not change compensation scope instance")
    @Test
    void submit_withExternalCompensationScope_passesSameScopeToTransitionExecute() {

        Ctx ctx = new Ctx(State.NEW);

        RecordingTransition transition = new RecordingTransition(State.NEW, Trigger.VALIDATE, State.VALIDATED)
                .withOutcomeSuccess();

        GenericStateMachine<State, Trigger, Ctx> sm =
                new GenericStateMachine<>(Ctx::getState, Ctx::setState, transition);

        CompensationScope external = new CompensationScope();

        sm.submit(ctx, Trigger.VALIDATE, external);
        assertSame(external, transition.lastScope.get(), "external scope must be passed through to execute()");
        assertEquals(State.VALIDATED, ctx.getState());
    }

    @DisplayName("submit() with external compensation scope --- does not change compensation scope instance")
    @Test
    void submit_whenExecuteThrows_andErrorConsumerProvided_consumesException_andCallsAfterExecution() {
        Ctx ctx = new Ctx(State.NEW);

        AtomicReference<Throwable> captured = new AtomicReference<>();

        RecordingTransition transition = new RecordingTransition(State.NEW, Trigger.VALIDATE, State.VALIDATED)
                .withExecuteThrows(new IllegalStateException("boom"))
                .withErrorConsumer((c, t) -> captured.set(t));

        GenericStateMachine<State, Trigger, Ctx> sm =
                new GenericStateMachine<>(Ctx::getState, Ctx::setState, transition);

        assertDoesNotThrow(() -> sm.submit(ctx, Trigger.VALIDATE));
        assertNotNull(captured.get(), "error consumer must be invoked");
        assertEquals("boom", captured.get().getMessage());
        assertEquals(State.NEW, ctx.getState());
        assertEquals(1, transition.afterCalls.get(), "afterExecution should run when exception is handled/consumed");
    }

    @DisplayName("submit() --- guarantees that afterExecution() is called even when an exception is thrown")
    @Test
    void submit_whenExecuteThrows_andNoErrorConsumer_rethrows_andCallAfterExecution() {
        Ctx ctx = new Ctx(State.NEW);

        RecordingTransition transition = new RecordingTransition(State.NEW, Trigger.VALIDATE, State.VALIDATED)
                .withExecuteThrows(new IllegalArgumentException("explode"));

        GenericStateMachine<State, Trigger, Ctx> sm =
                new GenericStateMachine<>(Ctx::getState, Ctx::setState, transition);

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> sm.submit(ctx, Trigger.VALIDATE)
        );
        assertEquals("explode", ex.getMessage());
        assertEquals(1, transition.afterCalls.get());
    }

    @DisplayName("ctor() --- throw exception if transition keys duplicate")
    @Test
    void ctor_duplicateTransitionKey_throwsIllegalStateException() {
        RecordingTransition t1 = new RecordingTransition(State.NEW, Trigger.VALIDATE, State.VALIDATED).withOutcomeSuccess();
        RecordingTransition t2 = new RecordingTransition(State.NEW, Trigger.VALIDATE, State.DONE).withOutcomeSuccess();

        assertThrows(IllegalStateException.class,
                () -> new GenericStateMachine<>(Ctx::getState, Ctx::setState, t1, t2));
    }

    /**
     * Minimal deterministic test double for Transition.
     * No mocks required; captures calls and allows controlled outcomes/exceptions.
     */
    static final class RecordingTransition extends Transition<State, Trigger, Ctx> {

        final AtomicInteger beforeCalls = new AtomicInteger();
        final AtomicInteger executeCalls = new AtomicInteger();
        final AtomicInteger afterCalls = new AtomicInteger();

        final AtomicReference<CompensationScope> lastScope = new AtomicReference<>();

        private boolean beforeResult = true;
        private Outcome<State, Ctx> outcome = Outcome.success(new Ctx(State.NEW));
        private RuntimeException toThrow;

        RecordingTransition(State from, Trigger trigger, State to) {
            super(from, trigger, to);
        }

        RecordingTransition withBeforeResult(boolean value) {
            this.beforeResult = value;
            return this;
        }

        RecordingTransition withOutcomeSuccess() {
            this.outcome = null;
            this.toThrow = null;
            return this;
        }

        RecordingTransition withOutcomeFailure(State failState) {
            this.outcome = Outcome.failure(null, failState);
            this.toThrow = null;
            return this;
        }

        RecordingTransition withExecuteThrows(RuntimeException ex) {
            this.toThrow = ex;
            return this;
        }

        RecordingTransition withErrorConsumer(BiConsumer<Ctx, Throwable> consumer) {
            this.errorConsumer = consumer;
            return this;
        }

        @Override
        public Outcome<State, Ctx> execute(Ctx context, CompensationScope cs) {
            executeCalls.incrementAndGet();
            lastScope.set(cs);

            if (toThrow != null) {
                throw toThrow;
            }

            if (outcome == null) {
                return Outcome.success(context);
            }

            if (outcome.getStatus() == Outcome.Status.FAILURE) {
                return Outcome.failure(context, outcome.getFailState());
            }

            return Outcome.success(context);
        }

        @Override
        protected boolean beforeExecution(Ctx context) {
            beforeCalls.incrementAndGet();
            return beforeResult;
        }

        @Override
        protected void afterExecution(Ctx context) {
            afterCalls.incrementAndGet();
        }
    }
}
