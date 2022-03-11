/*
 * Copyright (c) "Neo4j"
 * Neo4j Sweden AB [http://neo4j.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.gds.decisiontree;

import org.neo4j.gds.ml.core.samplers.IntUniformSamplerFromRange;

import java.util.SplittableRandom;

// NOTE: This class is not thead safe.
public final class FeatureBagger {

    private final IntUniformSamplerFromRange sampler;
    private final int totalNumberOfFeatures;
    private final int numberOfSamples;

    public FeatureBagger(SplittableRandom random, int totalNumberOfFeatures, double featureBaggingRatio) {
        assert Double.compare(featureBaggingRatio, 0) != 0 : "Invalid featureBaggingRatio";

        this.totalNumberOfFeatures = totalNumberOfFeatures;
        this.numberOfSamples = (int) Math.ceil(featureBaggingRatio * totalNumberOfFeatures);
        this.sampler = new IntUniformSamplerFromRange(random);
    }

    public int[] sample() {
        return sampler.sample(0, totalNumberOfFeatures, totalNumberOfFeatures, numberOfSamples, i -> false);
    }
}
