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

import org.neo4j.gds.core.utils.paged.HugeIntArray;
import org.neo4j.gds.core.utils.paged.HugeLongArray;
import org.neo4j.gds.ml.core.subgraph.LocalIdMap;

public class GiniIndex implements DecisionTreeLoss {

    private final HugeIntArray expectedMappedLabels;
    private final int numberOfClasses;

    public GiniIndex(HugeIntArray expectedMappedLabels, int numberOfClasses) {
        this.expectedMappedLabels = expectedMappedLabels;
        this.numberOfClasses = numberOfClasses;
    }

    public static GiniIndex fromOriginalLabels(
        HugeLongArray expectedOriginalLabels,
        LocalIdMap classMapping
    ) {
        assert expectedOriginalLabels.size() > 0;

        var mappedLabels = HugeIntArray.newArray(expectedOriginalLabels.size());
        mappedLabels.setAll(idx -> classMapping.toMapped(expectedOriginalLabels.get(idx)));

        return new GiniIndex(mappedLabels, classMapping.size());
    }

    @Override
    public double splitLoss(Groups groups, GroupSizes groupSizes) {
        long totalSize = groupSizes.left() + groupSizes.right();

        if (totalSize == 0) {
            throw new IllegalStateException("Cannot compute loss over only empty groups");
        }

        double loss = computeGroupLoss(groups.left(), groupSizes.left()) + computeGroupLoss(
            groups.right(),
            groupSizes.right()
        );

        return loss / totalSize;
    }

    private double computeGroupLoss(final HugeLongArray group, final long groupSize) {
        assert group.size() >= groupSize;

        if (groupSize == 0) return 0;

        final var groupClassCounts = new long[numberOfClasses];
        for (long i = 0; i < groupSize; i++) {
            int expectedLabel = expectedMappedLabels.get(group.get(i));
            groupClassCounts[expectedLabel]++;
        }

        double score = 0.0;
        for (var count : groupClassCounts) {
            score += Math.pow(count, 2);
        }

        score /= groupSize;

        return groupSize - score;
    }
}
