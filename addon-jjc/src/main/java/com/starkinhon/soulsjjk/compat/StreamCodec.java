package com.starkinhon.soulsjjk.compat;

import java.util.function.BiConsumer;
import java.util.function.Function;

// Backport of the 1.21 StreamCodec idea (encode + decode on a buffer) so the mod's payload records keep their shape on
// Forge 1.20.1. Only the parts the mod uses.
public interface StreamCodec<B, V> {
    V decode(B buf);

    void encode(B buf, V value);

    default <O> StreamCodec<B, O> apply(Function<StreamCodec<B, V>, StreamCodec<B, O>> op) {
        return op.apply(this);
    }

    default <O> StreamCodec<B, O> map(Function<? super V, ? extends O> to, Function<? super O, ? extends V> from) {
        StreamCodec<B, V> self = this;
        return new StreamCodec<>() {
            public O decode(B buf) {
                return to.apply(self.decode(buf));
            }

            public void encode(B buf, O value) {
                self.encode(buf, from.apply(value));
            }
        };
    }

    interface Encoder<B, V> {
        void encode(B buf, V value);
    }

    interface Decoder<B, V> {
        V decode(B buf);
    }

    interface MemberEncoder<B, V> {
        void encode(V value, B buf);
    }

    static <B, V> StreamCodec<B, V> of(Encoder<B, V> encoder, Decoder<B, V> decoder) {
        return new StreamCodec<>() {
            public V decode(B buf) {
                return decoder.decode(buf);
            }

            public void encode(B buf, V value) {
                encoder.encode(buf, value);
            }
        };
    }

    static <B, V> StreamCodec<B, V> ofMember(MemberEncoder<B, V> encoder, Decoder<B, V> decoder) {
        return of((buf, v) -> encoder.encode(v, buf), decoder);
    }

    static <B, V> StreamCodec<B, V> unit(V value) {
        return of((buf, v) -> {
        }, buf -> value);
    }

    interface F3<A, B, C, R> {
        R apply(A a, B b, C c);
    }

    interface F4<A, B, C, D, R> {
        R apply(A a, B b, C c, D d);
    }

    interface F5<A, B, C, D, E, R> {
        R apply(A a, B b, C c, D d, E e);
    }

    interface F6<A, B, C, D, E, F, R> {
        R apply(A a, B b, C c, D d, E e, F f);
    }

    static <B, C, T1> StreamCodec<B, C> composite(StreamCodec<? super B, T1> c1, Function<C, T1> g1, Function<T1, C> f) {
        return of((buf, v) -> c1.encode(buf, g1.apply(v)), buf -> f.apply(c1.decode(buf)));
    }

    static <B, C, T1, T2> StreamCodec<B, C> composite(StreamCodec<? super B, T1> c1, Function<C, T1> g1, StreamCodec<? super B, T2> c2,
            Function<C, T2> g2, java.util.function.BiFunction<T1, T2, C> f) {
        return of((buf, v) -> {
            c1.encode(buf, g1.apply(v));
            c2.encode(buf, g2.apply(v));
        }, buf -> {
            T1 a = c1.decode(buf);
            T2 b = c2.decode(buf);
            return f.apply(a, b);
        });
    }

    static <B, C, T1, T2, T3> StreamCodec<B, C> composite(StreamCodec<? super B, T1> c1, Function<C, T1> g1, StreamCodec<? super B, T2> c2,
            Function<C, T2> g2, StreamCodec<? super B, T3> c3, Function<C, T3> g3, F3<T1, T2, T3, C> f) {
        return of((buf, v) -> {
            c1.encode(buf, g1.apply(v));
            c2.encode(buf, g2.apply(v));
            c3.encode(buf, g3.apply(v));
        }, buf -> {
            T1 a = c1.decode(buf);
            T2 b = c2.decode(buf);
            T3 c = c3.decode(buf);
            return f.apply(a, b, c);
        });
    }

    static <B, C, T1, T2, T3, T4> StreamCodec<B, C> composite(StreamCodec<? super B, T1> c1, Function<C, T1> g1, StreamCodec<? super B, T2> c2,
            Function<C, T2> g2, StreamCodec<? super B, T3> c3, Function<C, T3> g3, StreamCodec<? super B, T4> c4, Function<C, T4> g4,
            F4<T1, T2, T3, T4, C> f) {
        return of((buf, v) -> {
            c1.encode(buf, g1.apply(v));
            c2.encode(buf, g2.apply(v));
            c3.encode(buf, g3.apply(v));
            c4.encode(buf, g4.apply(v));
        }, buf -> {
            T1 a = c1.decode(buf);
            T2 b = c2.decode(buf);
            T3 c = c3.decode(buf);
            T4 d = c4.decode(buf);
            return f.apply(a, b, c, d);
        });
    }

    static <B, C, T1, T2, T3, T4, T5> StreamCodec<B, C> composite(StreamCodec<? super B, T1> c1, Function<C, T1> g1, StreamCodec<? super B, T2> c2,
            Function<C, T2> g2, StreamCodec<? super B, T3> c3, Function<C, T3> g3, StreamCodec<? super B, T4> c4, Function<C, T4> g4,
            StreamCodec<? super B, T5> c5, Function<C, T5> g5, F5<T1, T2, T3, T4, T5, C> f) {
        return of((buf, v) -> {
            c1.encode(buf, g1.apply(v));
            c2.encode(buf, g2.apply(v));
            c3.encode(buf, g3.apply(v));
            c4.encode(buf, g4.apply(v));
            c5.encode(buf, g5.apply(v));
        }, buf -> {
            T1 a = c1.decode(buf);
            T2 b = c2.decode(buf);
            T3 c = c3.decode(buf);
            T4 d = c4.decode(buf);
            T5 e = c5.decode(buf);
            return f.apply(a, b, c, d, e);
        });
    }

    static <B, C, T1, T2, T3, T4, T5, T6> StreamCodec<B, C> composite(StreamCodec<? super B, T1> c1, Function<C, T1> g1, StreamCodec<? super B, T2> c2,
            Function<C, T2> g2, StreamCodec<? super B, T3> c3, Function<C, T3> g3, StreamCodec<? super B, T4> c4, Function<C, T4> g4,
            StreamCodec<? super B, T5> c5, Function<C, T5> g5, StreamCodec<? super B, T6> c6, Function<C, T6> g6, F6<T1, T2, T3, T4, T5, T6, C> f) {
        return of((buf, v) -> {
            c1.encode(buf, g1.apply(v));
            c2.encode(buf, g2.apply(v));
            c3.encode(buf, g3.apply(v));
            c4.encode(buf, g4.apply(v));
            c5.encode(buf, g5.apply(v));
            c6.encode(buf, g6.apply(v));
        }, buf -> {
            T1 a = c1.decode(buf);
            T2 b = c2.decode(buf);
            T3 c = c3.decode(buf);
            T4 d = c4.decode(buf);
            T5 e = c5.decode(buf);
            T6 f6 = c6.decode(buf);
            return f.apply(a, b, c, d, e, f6);
        });
    }
}
