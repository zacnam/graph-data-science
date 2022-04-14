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
package org.neo4j.gds.kmeans;

import org.neo4j.gds.api.properties.nodes.NodePropertyValues;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.SplittableRandom;

public class KmeansUniformSampler implements KmeansSampler {

    @Override
    public List<Long> sampleClusters(
        SplittableRandom random,
        NodePropertyValues nodePropertyValues,
        long nodeCount,
        int k
    ) {
        HashSet<Long> sampled = new HashSet<>();
        while (sampled.size() < k) {
            long nodeId = random.nextLong(nodeCount);
            if (!sampled.contains(nodeId)) {
                sampled.add(nodeId);
            }
        }
        return new ArrayList<>(sampled);
    }
}
