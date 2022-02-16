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
package org.neo4j.gds.ml.linkmodels.pipeline.logisticRegression;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.neo4j.gds.core.GraphDimensions;
import org.neo4j.gds.core.utils.mem.MemoryRange;

import static org.assertj.core.api.Assertions.assertThat;

class LinkLogisticRegressionDataTest {

    @ParameterizedTest
    @CsvSource(value = {
        "100,  0, 100,   56, 856",
        "1000, 0, 100,   56, 856",
        "100,  50, 500, 456, 4056",
        "1000, 50, 500, 456, 4056"
    })
    void shouldEstimateCorrectly(int relCount, int minFeatureCount, int maxFeatureCount, int minEstimation, int maxEstimation) {
        var estimatedFeatureCount = MemoryRange.of(minFeatureCount, maxFeatureCount);
        var dimensions = GraphDimensions.of(1000L, relCount);
        var memoryEstimation = LinkLogisticRegressionData
            .memoryEstimation(estimatedFeatureCount)
            .estimate(dimensions, 5000);

        assertThat(memoryEstimation.memoryUsage()).isEqualTo(MemoryRange.of(minEstimation, maxEstimation));
    }

}