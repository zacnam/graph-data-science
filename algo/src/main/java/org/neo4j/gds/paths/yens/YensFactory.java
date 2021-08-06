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
package org.neo4j.gds.paths.yens;

import org.neo4j.gds.AbstractAlgorithmFactory;
import org.neo4j.gds.api.Graph;
import org.neo4j.gds.core.utils.mem.AllocationTracker;
import org.neo4j.gds.core.utils.mem.MemoryEstimation;
import org.neo4j.gds.core.utils.progress.v2.tasks.ProgressTracker;
import org.neo4j.gds.core.utils.progress.v2.tasks.Task;
import org.neo4j.gds.core.utils.progress.v2.tasks.Tasks;
import org.neo4j.gds.paths.dijkstra.DijkstraFactory;
import org.neo4j.gds.paths.yens.config.ShortestPathYensBaseConfig;

import java.util.List;

public class YensFactory<CONFIG extends ShortestPathYensBaseConfig> extends AbstractAlgorithmFactory<Yens, CONFIG> {

    @Override
    public MemoryEstimation memoryEstimation(ShortestPathYensBaseConfig configuration) {
        return Yens.memoryEstimation();
    }

    @Override
    public Task progressTask(Graph graph, CONFIG config) {
        return Tasks.task(
            taskName(),
            DijkstraFactory.dijkstraProgressTask(graph),
            Tasks.iterativeOpen(
                "Searching path",
                () -> List.of(DijkstraFactory.dijkstraProgressTask(graph))
            )
        );
    }

    @Override
    protected String taskName() {
        return "Yens";
    }

    @Override
    protected Yens build(
        Graph graph, CONFIG configuration, AllocationTracker tracker, ProgressTracker progressTracker
    ) {
        return Yens.sourceTarget(graph, configuration, progressTracker, tracker);
    }
}
