package io.github.palette4j.state;

import io.github.palette4j.util.CompensationScope;
import java.util.Objects;
import java.util.function.BiConsumer;

/**
 * Represents a single, explicit state transition within a table-based state machine.
 * <p>
 * A {@code Transition} defines how a context {@code C} moves from one state {@code S}
 * to another in response to a trigger {@code T}. It encapsulates:
 * <ul>
 *   <li>the source state ({@code from})</li>
 *   <li>the triggering event or command ({@code trigger})</li>
 *   <li>the target state on successful execution ({@code to})</li>
 *   <li>the execution logic itself</li>
 *   <li>optional lifecycle hooks and error handling</li>
 * </ul>
 *
 * <h2>Execution Lifecycle</h2>
 * A transition participates in the following execution phases, orchestrated
 * by {@link GenericStateMachine}:
 * <ol>
 *   <li>{@link #beforeExecution(Object)} — optional pre-condition check</li>
 *   <li>{@link #execute(Object, CompensationScope)} — core transition logic</li>
 *   <li>Context state update based on {@link Outcome}</li>
 *   <li>{@link #afterExecution(Object)} — optional post-processing</li>
 * </ol>
 *
 * <h2>Outcome-Driven State Resolution</h2>
 * Transitions do not mutate state directly. Instead, they return an
 * {@link Outcome} that explicitly describes whether the transition:
 * <ul>
 *   <li>succeeded and should advance to {@code to}, or</li>
 *   <li>failed and should move to a designated failure state</li>
 * </ul>
 * This keeps transition logic deterministic and side-effect-aware.
 *
 * <h2>Compensation & Transactions</h2>
 * Transition execution occurs within a {@link CompensationScope}, allowing
 * implementations to register compensating actions for rollback-style behavior.
 * Scope ownership is managed by the caller (typically {@link GenericStateMachine}).
 *
 * <h2>Error Handling</h2>
 * If {@link #execute(Object, CompensationScope)} throws an exception:
 * <ul>
 *   <li>An optional {@link #getErrorConsumer()} is invoked if configured</li>
 *   <li>Otherwise, the exception is propagated to the caller</li>
 * </ul>
 *
 * <h2>Design Characteristics</h2>
 * <ul>
 *   <li>Transitions are immutable with respect to identity (from / trigger / to)</li>
 *   <li>Equality is based on transition definition, not execution outcome</li>
 *   <li>Intended to be stateless and reusable</li>
 * </ul>
 *
 * <h2>Example</h2>
 * <pre>{@code
 * Transition<State, Trigger, Context> reserveResource =
 *     new Transition<>(State.NEW, Trigger.RESERVE, State.RESERVED) {
 *
 *         @Override
 *         public Outcome<State, Context> execute(Context ctx, CompensationScope cs) {
 *
 *             Resource r = resourceService.reserve(ctx.getId());
 *
 *             // register compensation
 *             cs.onRollback(() -> resourceService.release(r));
 *
 *             if (r.isAvailable()) {
 *                 return Outcome.success(ctx);
 *             }
 *
 *             return Outcome.failure(ctx, State.FAILED);
 *         }
 *     };
 * }</pre>
 *
 * @param <S> the state type (typically an enum or value object)
 * @param <T> the trigger type (event, command, or signal)
 * @param <C> the context type carrying state and domain data
 *
 * @see GenericStateMachine
 * @see Outcome
 * @see CompensationScope
 */
public abstract class Transition<S, T, C> {

    /**
     * Represents the explicit result of a {@link Transition} execution.
     * <p>
     * An {@code Outcome} communicates both:
     * <ul>
     *   <li>the execution status (success or failure)</li>
     *   <li>the context associated with the transition</li>
     * </ul>
     * and, in the case of failure, the state to which the state machine should
     * transition.
     *
     * <h2>Outcome Semantics</h2>
     * <ul>
     *   <li>{@link Status#SUCCESS} — the state machine should advance to the
     *       transition's configured target state</li>
     *   <li>{@link Status#FAILURE} — the state machine should move to a designated
     *       failure state provided by the outcome</li>
     * </ul>
     *
     * <h2>Factory-Based Construction</h2>
     * Instances are created exclusively through static factory methods
     * to ensure semantic clarity:
     * <ul>
     *   <li>{@link #success(Object)}</li>
     *   <li>{@link #failure(Object, Object)}</li>
     * </ul>
     *
     * <h2>Design Notes</h2>
     * <ul>
     *   <li>Immutable and thread-safe</li>
     *   <li>Explicitly separates execution result from state mutation</li>
     *   <li>Encourages deterministic and testable transition logic</li>
     * </ul>
     *
     * <h2>Example</h2>
     * <pre>{@code
     * return condition
     *     ? Outcome.success(context)
     *     : Outcome.failure(context, State.REJECTED);
     *}</pre>
     *
     * @param <S> the state type
     * @param <C> the context type
     *
     * @see Transition
     * @see Status
     */
    public static class Outcome<S, C> {

        /**
         * Enumeration describing the execution status of a {@link Transition}.
         * <p>
         * This status determines how the enclosing state machine resolves the
         * next state after transition execution.
         *
         * <ul>
         *   <li>{@link #SUCCESS} — transition completed successfully</li>
         *   <li>{@link #FAILURE} — transition completed with a business-level failure</li>
         * </ul>
         *
         * <h2>Design Intent</h2>
         * This enum intentionally represents <b>logical outcomes</b>, not exceptions.
         * Exceptional conditions are handled separately via error consumers or
         * propagated exceptions.
         */
        public enum Status {
            SUCCESS,
            FAILURE
        }

        private final C context;
        private final Status status;
        private final S failState;

        private Outcome(C context, Status status, S failState) {
            this.context = context;
            this.status = status;
            this.failState = failState;
        }

        public static <S, C> Outcome<S, C> success(C context) {
            return new Outcome<>(context, Status.SUCCESS, null);
        }

        public static <S, C> Outcome<S, C> failure(C context, S failState) {
            return new Outcome<>(context, Status.FAILURE, failState);
        }

        public C getContext() {
            return context;
        }

        public Status getStatus() {
            return status;
        }

        public S getFailState() {
            return failState;
        }
    }

    protected final S from;
    protected final T trigger;
    protected final S to;
    protected BiConsumer<C, Throwable> errorConsumer;

    public Transition(S from, T trigger, S to) {
        this.from = from;
        this.trigger = trigger;
        this.to = to;
    }

    public Transition(S from, T trigger, S to, BiConsumer<C, Throwable> errorConsumer) {
        this.from = from;
        this.trigger = trigger;
        this.to = to;
        this.errorConsumer = errorConsumer;
    }

    public abstract Outcome<S, C> execute(C context, CompensationScope cs);

    /**
     * A hook method that is called before the main execution logic of the transition.
     * <p>
     * Subclasses can override this method to perform preliminary checks or setup operations.
     * If this method returns {@code false}, the transition will be aborted before the
     * {@link #execute(Object, CompensationScope)} method is called.
     *
     * @param context The context object associated with the state machine.
     * @return {@code true} to proceed with the execution, {@code false} to abort the transition.
     */
    protected boolean beforeExecution(C context) {
        return true;
    }

    /**
     * A hook method that is called after the main execution logic of the transition.
     * <p>
     * Subclasses can override this method to perform post-processing checks or setup operations.
     *
     * @param context The context object associated with the state machine.
     */
    protected void afterExecution(C context) {
    }

    public S getFrom() {
        return from;
    }

    public T getTrigger() {
        return trigger;
    }

    public S getTo() {
        return to;
    }

    public BiConsumer<C, Throwable> getErrorConsumer() {
        return errorConsumer;
    }

    public void setErrorConsumer(BiConsumer<C, Throwable> onErrorConsumer) {
        this.errorConsumer = onErrorConsumer;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Transition)) return false;
        Transition<?, ?, ?> that = (Transition<?, ?, ?>) o;
        return Objects.equals(from, that.from) && Objects.equals(trigger, that.trigger) && Objects.equals(to, that.to) && Objects.equals(errorConsumer, that.errorConsumer);
    }

    @Override
    public int hashCode() {
        return Objects.hash(from, trigger, to, errorConsumer);
    }

    @Override
    public String toString() {
        return "Transition{" +
                "from=" + from +
                ", trigger=" + trigger +
                ", to=" + to +
                ", errorConsumer=" + errorConsumer +
                '}';
    }
}
