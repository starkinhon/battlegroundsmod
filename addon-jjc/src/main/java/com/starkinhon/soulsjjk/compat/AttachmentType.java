package com.starkinhon.soulsjjk.compat;

import java.util.function.Supplier;

import com.mojang.serialization.Codec;

// NeoForge's data attachments, rebuilt for Forge 1.20.1 (stored by Att).
public final class AttachmentType<T> {
    final Supplier<T> defaults;
    final Codec<T> codec;
    final boolean copyOnDeath;
    String name = "";

    private AttachmentType(Supplier<T> defaults, Codec<T> codec, boolean copyOnDeath) {
        this.defaults = defaults;
        this.codec = codec;
        this.copyOnDeath = copyOnDeath;
    }

    public static <T> Builder<T> builder(Supplier<T> defaults) {
        return new Builder<>(defaults);
    }

    public static final class Builder<T> {
        private final Supplier<T> defaults;
        private Codec<T> codec;
        private boolean copyOnDeath;

        Builder(Supplier<T> defaults) {
            this.defaults = defaults;
        }

        public Builder<T> serialize(Codec<T> codec) {
            this.codec = codec;
            return this;
        }

        public Builder<T> copyOnDeath() {
            this.copyOnDeath = true;
            return this;
        }

        public AttachmentType<T> build() {
            return new AttachmentType<>(defaults, codec, copyOnDeath);
        }
    }
}
