"""Registry of every challenge arena: id -> builder returning (Vox, meta)."""
ARENAS = {}

def arena(aid):
    def deco(fn):
        ARENAS[aid] = fn
        return fn
    return deco

import arena_naruto  # noqa: E402,F401
import arena_db  # noqa: E402,F401
import arena_ds  # noqa: E402,F401
import arena_bleach  # noqa: E402,F401
import arena_jujutsu  # noqa: E402,F401
import arena_novos  # noqa: E402,F401
