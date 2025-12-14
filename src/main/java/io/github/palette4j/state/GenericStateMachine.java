package io.github.palette4j.state;

import io.github.palette4j.util.CompensationScope;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Function;

import static io.github.palette4j.util.CollectionValidator.COLLECTION_CONTAINS_NULL_ELEMENTS;


/**
 * A generic, table-based state machine implementation with explicit transition orchestration,
 * lifecycle hooks, and optional transactional compensation support.
 * <p>
 * This state machine is driven by a transition table defined as a mapping of
 * {@code (currentState, trigger)} pairs to {@link Transition} instances.
 * Each transition encapsulates:
 * <ul>
 *   <li>a source state</li>
 *   <li>a triggering event</li>
 *   <li>a target state</li>
 *   <li>optional pre- and post-execution hooks</li>
 *   <li>optional error handling</li>
 * </ul>
 *
 * <h2>Execution Model</h2>
 * For a given context {@code C} and trigger {@code T}, the state machine:
 * <ol>
 *   <li>Extracts the current state from the context using a {@code stateExtractor}</li>
 *   <li>Resolves a transition using {@code (state, trigger)} lookup</li>
 *   <li>Invokes {@link Transition#beforeExecution(Object)} to determine executability</li>
 *   <li>Executes the transition logic within a {@link CompensationScope}</li>
 *   <li>Updates the context state based on transition outcome</li>
 *   <li>Invokes {@link Transition#afterExecution(Object)} unconditionally</li>
 * </ol>
 *
 * <h2>Compensation & Transaction Boundaries</h2>
 * The state machine supports two execution modes:
 * <ul>
 *   <li>
 *     <b>Externally managed compensation</b> — via
 *     {@link GenericStateMachine#submit(Object, Object, CompensationScope)}, allowing multiple transitions
 *     to participate in a shared transactional scope.
 *   </li>
 *   <li>
 *     <b>Self-managed compensation</b> — via {@link GenericStateMachine#submit(Object, Object)}, where
 *     a new {@link CompensationScope} is created, committed, and closed automatically.
 *   </li>
 * </ul>
 *
 * <h2>Error Handling</h2>
 * If transition execution throws an exception:
 * <ul>
 *   <li>An optional {@link Transition#getErrorConsumer()} is invoked if present</li>
 *   <li>Otherwise, the exception is propagated to the caller</li>
 * </ul>
 * State updates are only applied after successful transition execution.
 *
 * <h2>Design Characteristics</h2>
 * <ul>
 *   <li>Deterministic and explicit transition resolution</li>
 *   <li>No implicit state mutation — state updates are delegated via {@code stateUpdater}</li>
 *   <li>Transition uniqueness enforced at construction time</li>
 *   <li>Thread-safety depends on the immutability of transitions and context usage</li>
 * </ul>
 *
 * <h2>Example</h2>
 * <pre>{@code
 * enum State { NEW, VALIDATED, FAILED }
 * enum Trigger { VALIDATE }
 *
 * final class Context {
 *     private State state;
 *     State getState() { return state; }
 *     void setState(State state) { this.state = state; }
 * }
 *
 * Transition<State, Trigger, Context> validate =
 *     new Transition<>(State.NEW, Trigger.VALIDATE, State.VALIDATED) {
 *         @Override
 *         public Outcome<State, Context> execute(Context ctx, CompensationScope cs) {
 *             if (isValid(ctx)) {
 *                 return Outcome.success(ctx);
 *             }
 *             return Outcome.failure(ctx, State.FAILED);
 *         }
 *     };
 *
 * GenericStateMachine<State, Trigger, Context> sm =
 *     new GenericStateMachine<>(
 *         Context::getState,
 *         Context::setState,
 *         validate
 *     );
 *
 * Context ctx = new Context();
 * ctx.setState(State.NEW);
 *
 * sm.submit(ctx, Trigger.VALIDATE);
 * // ctx.state == VALIDATED or FAILED
 * }</pre>
 *
 * @param <S> the state type (typically an enum or value object)
 * @param <T> the trigger type (event, command, or signal)
 * @param <C> the context type carrying state and domain data
 * @see Transition
 * @see Transition.Outcome
 * @see CompensationScope
 */
public class GenericStateMachine<S, T, C> {

    /**
     * Immutable composite key representing a unique state machine transition.
     * <p>
     * A {@code TransitionKey} is defined by a pair of:
     * <ul>
     *   <li>the current (source) state</li>
     *   <li>the trigger initiating the transition</li>
     * </ul>
     * <p>
     * This key is used internally by {@link GenericStateMachine} as the lookup key
     * for resolving transitions from the transition registry.
     *
     * <h2>Equality Contract</h2>
     * Two {@code TransitionKey} instances are considered equal if and only if both
     * their {@code currentState} and {@code trigger} values are equal.
     * This ensures deterministic transition resolution and prevents ambiguous
     * state-trigger mappings.
     *
     * <h2>Design Notes</h2>
     * <ul>
     *   <li>Instances are immutable and safe for use as {@link Map} keys</li>
     *   <li>Intended strictly for internal use</li>
     *   <li>Prevents accidental duplication of transitions at construction time</li>
     * </ul>
     *
     * @param <S> the state type
     * @param <T> the trigger type
     */
    protected static final class TransitionKey<S, T> {
        private final S currentState;
        private final T trigger;

        TransitionKey(S currentState, T trigger) {
            this.currentState = currentState;
            this.trigger = trigger;
        }

        public S getCurrentState() {
            return currentState;
        }

        public T getTrigger() {
            return trigger;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof TransitionKey)) return false;
            TransitionKey<?, ?> that = (TransitionKey<?, ?>) o;
            return Objects.equals(currentState, that.currentState) && Objects.equals(trigger, that.trigger);
        }

        @Override
        public int hashCode() {
            return Objects.hash(currentState, trigger);
        }

        @Override
        public String toString() {
            return "TransitionKey{" +
                    "currentState=" + currentState +
                    ", trigger=" + trigger +
                    '}';
        }
    }

    private final Map<TransitionKey<S, T>, Transition<S, T, C>> transitionsRegistry = new HashMap<>();
    private final Function<C, S> stateExtractor;
    private final BiConsumer<C, S> stateUpdater;


    @SafeVarargs
    public GenericStateMachine(final Function<C, S> stateExtractor,
                               final BiConsumer<C, S> stateUpdater,
                               Transition<S, T, C>... transitions
    ) {
        this(stateExtractor, stateUpdater, Arrays.asList(transitions));
    }

    public GenericStateMachine(
            final Function<C, S> stateExtractor,
            final BiConsumer<C, S> stateUpdater,
            List<Transition<S, T, C>> transitions
    ) {
        COLLECTION_CONTAINS_NULL_ELEMENTS.not().attest(transitions, () -> new IllegalArgumentException("Provided transitions contains invalid elements"));
        if (stateExtractor == null) {
            throw new IllegalArgumentException("Provided state extractor is null");
        } else if (stateUpdater == null) {
            throw new IllegalArgumentException("Provided state updater is null");
        }

        transitions.forEach(transition -> {
                    TransitionKey<S, T> key = new TransitionKey<>(transition.getFrom(), transition.getTrigger());
                    if (transitionsRegistry.containsKey(key)) {
                        throw new IllegalStateException("Transition already exists");
                    }
                    transitionsRegistry.put(new TransitionKey<>(transition.getFrom(), transition.getTrigger()), transition);
                }
        );

        this.stateExtractor = stateExtractor;
        this.stateUpdater = stateUpdater;
    }

    /**
     * Processes a state transition for a given context and trigger within a specified compensation scope.
     * <p>
     * This method finds and executes the appropriate transition based on the context's current state and the provided trigger.
     * The transition is executed within the provided {@link CompensationScope}, allowing for multiple operations
     * to be grouped into a single transaction.
     *
     * @param context The context object containing the current state and other relevant data.
     *                This object will be modified by the transition.
     * @param trigger The trigger that initiates the state transition.
     * @param cs      The {@link CompensationScope} for the transition, allowing it to be part of a larger transaction.
     * @return The context object, potentially modified with a new state after the transition.
     */
    public C submit(C context, T trigger, CompensationScope cs) {
        return submitInternal(context, trigger, cs);
    }

    /**
     * Processes a state transition for a given context and trigger.
     * <p>
     * This method finds and executes the appropriate transition based on the context's current state and the provided trigger.
     * This is a convenience method that creates and manages its own {@link CompensationScope}, making the transition
     * execute within its own transaction. For chaining multiple transitions in a single transaction, use
     * {@link #submit(Object, Object, CompensationScope)}.
     *
     * @param context The context object containing the current state and other relevant data.
     *                This object will be modified by the transition.
     * @param trigger The trigger that initiates the state transition.
     * @return The context object, potentially modified with a new state after the transition.
     */
    public C submit(C context, T trigger) {
        return submitInternal(context, trigger, null);
    }

    /**
     * Internal method to process a state transition for a given context and trigger.
     * It finds and executes the appropriate transition, updating the context's state accordingly.
     * This method also manages compensation scopes for transactional behavior.
     *
     * @param context The context object containing the current state and other relevant data.
     *                This object will be modified by the transition.
     * @param trigger The trigger that initiates the state transition.
     * @param cs      The {@link CompensationScope} for the transition. If null, a new scope is created
     *                and committed internally. If provided, the transition is executed within this scope,
     *                allowing for chaining multiple transitions in a single transaction.
     * @return The context object, potentially modified with a new state after the transition.
     */
    private C submitInternal(C context, T trigger, CompensationScope cs) {
        S stateBeforeTransition = stateExtractor.apply(context);
        Transition<S, T, C> transition = transitionsRegistry.get(new TransitionKey<>(stateBeforeTransition, trigger));

        if (transition == null || !transition.beforeExecution(context)) {
            return context;
        }

        Transition.Outcome<S, C> outcome;

        try {
            if (cs == null) {
                try (CompensationScope scope = new CompensationScope()) {
                    outcome = transition.execute(context, scope);
                    updateContextState(transition, outcome, context, stateUpdater);
                    scope.commit();
                }
            } else {
                outcome = transition.execute(context, cs);
                updateContextState(transition, outcome, context, stateUpdater);
            }
        } catch (Exception e) {
            BiConsumer<C, Throwable> onErrorConsumer = transition.getErrorConsumer();
            if (onErrorConsumer != null) {
                onErrorConsumer.accept(context, e);
            } else {
                throw e;
            }
        } finally {
            transition.afterExecution(context);
        }

        return context;
    }

    private void updateContextState(Transition<S, T, C> transition,
                                    Transition.Outcome<S, C> outcome,
                                    C context,
                                    BiConsumer<C, S> stateUpdater
    ) {
        if (outcome.getStatus() == Transition.Outcome.Status.SUCCESS) {
            stateUpdater.accept(context, transition.getTo());
        } else {
            stateUpdater.accept(context, outcome.getFailState());
        }
    }

    public Function<C, S> getStateExtractor() {
        return stateExtractor;
    }

    public BiConsumer<C, S> getStateUpdater() {
        return stateUpdater;
    }
}
