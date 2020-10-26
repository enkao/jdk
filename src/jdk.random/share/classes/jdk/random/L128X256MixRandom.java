/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package jdk.random;

import java.math.BigInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.Map;
import java.util.random.RandomGenerator;
import java.util.random.internal.RandomSupport;
import java.util.random.internal.RandomSupport.AbstractSplittableWithBrineGenerator;


/**
 * {@preview Associated with random number generators, a preview feature of
 *           the Java core libraries.
 *
 *           This class is associated with <i>random number generators</i>,
 *           a preview feature of the Java core libraries. Programs can only use
 *           this class when preview features are enabled. Preview features
 *           may be removed in a future release, or upgraded to permanent
 *           features of the Java core libraries.}
 *
 * A "splittable" pseudorandom number generator (PRNG) whose period
 * is roughly 2<sup>384</sup>.  Class {@link L128X256MixRandom} implements
 * interfaces {@link RandomGenerator} and {@link SplittableGenerator},
 * and therefore supports methods for producing pseudorandomly chosen
 * values of type {@code int}, {@code long}, {@code float}, {@code double},
 * and {@code boolean} (and for producing streams of pseudorandomly chosen
 * numbers of type {@code int}, {@code long}, and {@code double}),
 * as well as methods for creating new split-off {@link L128X256MixRandom}
 * objects or streams of such objects.
 *
 * <p>The {@link L128X256MixRandom} algorithm is a specific member of
 * the LXM family of algorithms for pseudorandom number generators;
 * for more information, see the documentation for package
 * {@link jdk.random}.  Each instance of {@link L128X256MixRandom}
 * has 384 bits of state plus one 128-bit instance-specific parameter.
 *
 * <p>If two instances of {@link L128X256MixRandom} are created with
 * the same seed within the same program execution, and the same
 * sequence of method calls is made for each, they will generate and
 * return identical sequences of values.
 *
 * <p>As with {@link java.util.SplittableRandom}, instances of
 * {@link L128X256MixRandom} are <em>not</em> thread-safe.  They are
 * designed to be split, not shared, across threads (see the {@link #split}
 * method). For example, a {@link java.util.concurrent.ForkJoinTask}
 * fork/join-style computation using random numbers might include a
 * construction of the form
 * {@code new Subtask(someL128X256MixRandom.split()).fork()}.
 *
 * <p>This class provides additional methods for generating random
 * streams, that employ the above techniques when used in
 * {@code stream.parallel()} mode.
 *
 * <p>Instances of {@link L128X256MixRandom} are not cryptographically
 * secure.  Consider instead using {@link java.security.SecureRandom}
 * in security-sensitive applications. Additionally,
 * default-constructed instances do not use a cryptographically random
 * seed unless the {@linkplain System#getProperty system property}
 * {@code java.util.secureRandomSeed} is set to {@code true}.
 *
 * @since   16
 *
 * @jdk.internal.PreviewFeature(feature= PreviewFeature.Feature.RANDOM_NUMBERS,
 *          essentialAPI=true)
 * @SuppressWarnings("preview")
 */
public final class L128X256MixRandom extends AbstractSplittableWithBrineGenerator {

    /*
     * Implementation Overview.
     *
     * The 128-bit parameter `a` is represented as two long fields `ah` and `al`.
     * The 128-bit state variable `s` is represented as two long fields `sh` and `sl`.
     *
     * The split operation uses the current generator to choose eight
     * new 64-bit long values that are then used to initialize the
     * parameters `ah` and `al` and the state variables `sh`, `sl`,
     * `x0`, `x1`, `x2`, and `x3` for a newly constructed generator.
     *
     * With extremely high probability, no two generators so chosen
     * will have the same `a` parameter, and testing has indicated
     * that the values generated by two instances of {@link L128X256MixRandom}
     * will be (approximately) independent if have different values for `a`.
     *
     * The default (no-argument) constructor, in essence, uses
     * "defaultGen" to generate eight new 64-bit values for the same
     * purpose.  Multiple generators created in this way will certainly
     * differ in their `a` parameters.  The defaultGen state must be accessed
     * in a thread-safe manner, so we use an AtomicLong to represent
     * this state.  To bootstrap the defaultGen, we start off using a
     * seed based on current time unless the
     * java.util.secureRandomSeed property is set. This serves as a
     * slimmed-down (and insecure) variant of SecureRandom that also
     * avoids stalls that may occur when using /dev/random.
     *
     * File organization: First static fields, then instance
     * fields, then constructors, then instance methods.
     */

    /* ---------------- static fields ---------------- */

    /**
     * The seed generator for default constructors.
     */
    private static final AtomicLong defaultGen = new AtomicLong(RandomSupport.initialSeed());

    /*
     * The period of this generator, which is (2**256 - 1) * 2**128.
     */
    private static final BigInteger PERIOD =
        BigInteger.ONE.shiftLeft(256).subtract(BigInteger.ONE).shiftLeft(128);

    /*
     * Number of bits used to maintain state of seed.
     */
    private static final int STATE_BITS = 384;

    /*
     * RandomGenerator properties.
     */
    static Map<RandomGeneratorProperty, Object> getProperties() {
        return Map.ofEntries(
                Map.entry(RandomGeneratorProperty.NAME, "L128X256MixRandom"),
                Map.entry(RandomGeneratorProperty.GROUP, "LXM"),
                Map.entry(RandomGeneratorProperty.PERIOD, PERIOD),
                Map.entry(RandomGeneratorProperty.STATE_BITS, STATE_BITS),
                Map.entry(RandomGeneratorProperty.EQUIDISTRIBUTION, EQUIDISTRIBUTION),
                Map.entry(RandomGeneratorProperty.IS_STOCHASTIC, false),
                Map.entry(RandomGeneratorProperty.IS_HARDWARE, false)
        );
    }

    /*
     * The equidistribution of the algorithm.
     */
    private static final int EQUIDISTRIBUTION = 1;

    /*
     * Low half of multiplier used in the LCG portion of the algorithm;
     * the overall multiplier is (2**64 + ML).
     * Chosen based on research by Sebastiano Vigna and Guy Steele (2019).
     * The spectral scores for dimensions 2 through 8 for the multiplier 0x1d605bbb58c8abbfdLL
     * are [0.991889, 0.907938, 0.830964, 0.837980, 0.780378, 0.797464, 0.761493].
     */

    private static final long ML = 0xd605bbb58c8abbfdL;

    /* ---------------- instance fields ---------------- */

    /**
     * The parameter that is used as an additive constant for the LCG.
     * Must be odd (therefore al must be odd).
     */
    private final long ah, al;

    /**
     * The per-instance state: sh and sl for the LCG; x0, x1, x2, and x3 for the xorshift.
     * At least one of the four fields x0, x1, x2, and x3 must be nonzero.
     */
    private long sh, sl, x0, x1, x2, x3;

    /* ---------------- constructors ---------------- */

    /**
     * Basic constructor that initializes all fields from parameters.
     * It then adjusts the field values if necessary to ensure that
     * all constraints on the values of fields are met.
     *
     * @param ah high half of the additive parameter for the LCG
     * @param al low half of the additive parameter for the LCG
     * @param sh high half of the initial state for the LCG
     * @param sl low half of the initial state for the LCG
     * @param x0 first word of the initial state for the xorshift generator
     * @param x1 second word of the initial state for the xorshift generator
     * @param x2 third word of the initial state for the xorshift generator
     * @param x3 fourth word of the initial state for the xorshift generator
     */
    public L128X256MixRandom(long ah, long al, long sh, long sl, long x0, long x1, long x2, long x3) {
        // Force a to be odd.
        this.ah = ah;
        this.al = al | 1;
        this.sh = sh;
        this.sl = sl;
        this.x0 = x0;
        this.x1 = x1;
        this.x2 = x2;
        this.x3 = x3;
        // If x0, x1, x2, and x3 are all zero, we must choose nonzero values.
        if ((x0 | x1 | x2 | x3) == 0) {
       long v = sh;
            // At least three of the four values generated here will be nonzero.
            this.x0 = RandomSupport.mixStafford13(v += RandomSupport.GOLDEN_RATIO_64);
            this.x1 = RandomSupport.mixStafford13(v += RandomSupport.GOLDEN_RATIO_64);
            this.x2 = RandomSupport.mixStafford13(v += RandomSupport.GOLDEN_RATIO_64);
            this.x3 = RandomSupport.mixStafford13(v + RandomSupport.GOLDEN_RATIO_64);
        }
    }

    /**
     * Creates a new instance of {@link L128X256MixRandom} using the
     * specified {@code long} value as the initial seed. Instances of
     * {@link L128X256MixRandom} created with the same seed in the same
     * program generate identical sequences of values.
     *
     * @param seed the initial seed
     */
    public L128X256MixRandom(long seed) {
        // Using a value with irregularly spaced 1-bits to xor the seed
        // argument tends to improve "pedestrian" seeds such as 0 or
        // other small integers.  We may as well use SILVER_RATIO_64.
        //
        // The seed is hashed by mixMurmur64 to produce the `a` parameter.
        // The seed is hashed by mixStafford13 to produce the initial `x0`,
        // which will then be used to produce the first generated value.
        // The other x values are filled in as if by a SplitMix PRNG with
        // GOLDEN_RATIO_64 as the gamma value and mixStafford13 as the mixer.
        this(RandomSupport.mixMurmur64(seed ^= RandomSupport.SILVER_RATIO_64),
             RandomSupport.mixMurmur64(seed += RandomSupport.GOLDEN_RATIO_64),
             0,
             1,
             RandomSupport.mixStafford13(seed),
             RandomSupport.mixStafford13(seed += RandomSupport.GOLDEN_RATIO_64),
             RandomSupport.mixStafford13(seed += RandomSupport.GOLDEN_RATIO_64),
             RandomSupport.mixStafford13(seed + RandomSupport.GOLDEN_RATIO_64));
    }

    /**
     * Creates a new instance of {@link L128X256MixRandom} that is likely to
     * generate sequences of values that are statistically independent
     * of those of any other instances in the current program execution,
     * but may, and typically does, vary across program invocations.
     */
    public L128X256MixRandom() {
        // Using GOLDEN_RATIO_64 here gives us a good Weyl sequence of values.
        this(defaultGen.getAndAdd(RandomSupport.GOLDEN_RATIO_64));
    }

    /**
     * Creates a new instance of {@link L128X256MixRandom} using the specified array of
     * initial seed bytes. Instances of {@link L128X256MixRandom} created with the same
     * seed array in the same program execution generate identical sequences of values.
     *
     * @param seed the initial seed
     */
    public L128X256MixRandom(byte[] seed) {
        // Convert the seed to 6 long values, of which the last 4 are not all zero.
        long[] data = RandomSupport.convertSeedBytesToLongs(seed, 6, 4);
        long ah = data[0], al = data[1], sh = data[2], sl = data[3],
             x0 = data[4], x1 = data[5], x2 = data[6], x3 = data[7];
        // Force a to be odd.
        this.ah = ah;
        this.al = al | 1;
        this.sh = sh;
        this.sl = sl;
        this.x0 = x0;
        this.x1 = x1;
        this.x2 = x2;
        this.x3 = x3;
    }

    /* ---------------- public methods ---------------- */

    @Override
    public SplittableGenerator split(SplittableGenerator source, long brine) {
       // Pick a new instance "at random", but use the brine for (the low half of) `a`.
        return new L128X256MixRandom(source.nextLong(), brine << 1,
                    source.nextLong(), source.nextLong(),
                    source.nextLong(), source.nextLong(),
                    source.nextLong(), source.nextLong());
    }

    @Override
    public long nextLong() {
       // Compute the result based on current state information
       // (this allows the computation to be overlapped with state update).
        final long result = RandomSupport.mixLea64(sh + x0);

       // Update the LCG subgenerator
        // The LCG is, in effect, s = ((1LL << 64) + ML) * s + a, if only we had 128-bit arithmetic.
        final long u = ML * sl;
       // Note that Math.multiplyHigh computes the high half of the product of signed values,
       // but what we need is the high half of the product of unsigned values; for this we use the
       // formula "unsignedMultiplyHigh(a, b) = multiplyHigh(a, b) + ((a >> 63) & b) + ((b >> 63) & a)";
       // in effect, each operand is added to the result iff the sign bit of the other operand is 1.
       // (See Henry S. Warren, Jr., _Hacker's Delight_ (Second Edition), Addison-Wesley (2013),
       // Section 8-3, p. 175; or see the First Edition, Addison-Wesley (2003), Section 8-3, p. 133.)
       // If Math.unsignedMultiplyHigh(long, long) is ever implemented, the following line can become:
       //         sh = (ML * sh) + Math.unsignedMultiplyHigh(ML, sl) + sl + ah;
       // and this entire comment can be deleted.
        sh = (ML * sh) + (Math.multiplyHigh(ML, sl) + ((ML >> 63) & sl) + ((sl >> 63) & ML)) + sl + ah;
        sl = u + al;
        if (Long.compareUnsigned(sl, u) < 0) ++sh;  // Handle the carry propagation from low half to high half.

       // Update the Xorshift subgenerator
        long q0 = x0, q1 = x1, q2 = x2, q3 = x3;
        {   // xoshiro256 1.0
            long t = q1 << 17;
            q2 ^= q0;
            q3 ^= q1;
            q1 ^= q2;
            q0 ^= q3;
            q2 ^= t;
            q3 = Long.rotateLeft(q3, 45);
        }
        x0 = q0; x1 = q1; x2 = q2; x3 = q3;

        return result;
    }

}
