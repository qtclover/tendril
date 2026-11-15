package io.github.qtclover.tendril.internal;

/** One hooked call instruction inside a hooked method. Like {@link Site}, looked up by id at call time. */
final class InvokePoint {

    final int id;
    final Site site;
    /** Position of the instruction among all call instructions of the method. */
    final int index;
    final CallRef call;

    volatile InjectionImpl[] before = Site.NONE;
    volatile InjectionImpl[] after = Site.NONE;

    InvokePoint(int id, Site site, int index, CallRef call) {
        this.id = id;
        this.site = site;
        this.index = index;
        this.call = call;
    }
}
