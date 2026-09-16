package fr.tropimon.chatfilter;

/** One token per resource reload, including font packs reloaded without changing language. */
public final class RenderCacheEpoch {
    private static volatile Object current = new Object();
    private RenderCacheEpoch() { }
    public static Object current() { return current; }
    public static void invalidate() { current = new Object(); }
}
