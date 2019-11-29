/*
 * Copyright (c) 2017-2019 "Neo4j,"
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
package org.neo4j.graphalgo;

import org.neo4j.graphalgo.api.Graph;
import org.neo4j.graphalgo.api.GraphFactory;
import org.neo4j.graphalgo.core.CypherMapWrapper;
import org.neo4j.graphalgo.core.GraphDimensions;
import org.neo4j.graphalgo.core.GraphLoader;
import org.neo4j.graphalgo.core.loading.GraphCatalog;
import org.neo4j.graphalgo.core.utils.Pools;
import org.neo4j.graphalgo.core.utils.TerminationFlag;
import org.neo4j.graphalgo.core.utils.mem.MemoryEstimation;
import org.neo4j.graphalgo.core.utils.mem.MemoryEstimations;
import org.neo4j.graphalgo.core.utils.mem.MemoryTree;
import org.neo4j.graphalgo.core.utils.mem.MemoryTreeWithDimensions;
import org.neo4j.graphalgo.core.utils.paged.AllocationTracker;
import org.neo4j.graphalgo.newapi.BaseAlgoConfig;
import org.neo4j.graphalgo.newapi.GraphCreateConfig;
import org.neo4j.helpers.collection.Pair;

import java.util.Map;
import java.util.Optional;

public abstract class BaseAlgoProc<A extends Algorithm<A>, CONFIG extends BaseAlgoConfig> extends BaseProc<CONFIG> {

    protected abstract CONFIG newConfig(
        Optional<String> graphName,
        CypherMapWrapper config
    );

    protected final A newAlgorithm(
            final Graph graph,
            final CONFIG config,
            final AllocationTracker tracker) {
        TerminationFlag terminationFlag = TerminationFlag.wrap(transaction);
        return algorithmFactory(config)
                .build(graph, config, tracker, log)
                .withProgressLogger(log)
                .withTerminationFlag(terminationFlag);
    }

    protected abstract GraphLoader configureGraphLoader(GraphLoader loader, CONFIG config);

    protected abstract AlgorithmFactory<A, CONFIG> algorithmFactory(CONFIG config);

    protected MemoryTreeWithDimensions memoryEstimation(final CONFIG config) {
        GraphLoader loader = newLoader(AllocationTracker.EMPTY, config);
        GraphFactory graphFactory = loader.build(config.getGraphImpl());
        GraphDimensions dimensions = graphFactory.dimensions();
        AlgorithmFactory<A, CONFIG> algorithmFactory = algorithmFactory(config);
        MemoryEstimations.Builder estimationsBuilder = MemoryEstimations.builder("graph with procedure");

        MemoryEstimation graphMemoryEstimation = config.estimate(loader.toSetup(), graphFactory);
        estimationsBuilder.add(graphMemoryEstimation)
            .add(algorithmFactory.memoryEstimation());

        MemoryTree memoryTree = estimationsBuilder.build().estimate(dimensions, config.concurrency());

        return new MemoryTreeWithDimensions(memoryTree, dimensions);
    }

    @Override
    protected GraphLoader newConfigureLoader(GraphLoader loader, CONFIG config) {
        return configureGraphLoader(loader, config);
    }

    protected Pair<CONFIG, Optional<String>> processInput(Object graphNameOrConfig, Map<String, Object> configuration) {

        CONFIG config;
        Optional<String> graphName = Optional.empty();

        if (graphNameOrConfig instanceof String) {
            graphName = Optional.of((String) graphNameOrConfig);
            CypherMapWrapper algoConfig = CypherMapWrapper.create(configuration);
            config = newConfig(graphName, algoConfig);

            //TODO: assert that algoConfig is empty or fail
        } else if (graphNameOrConfig instanceof Map) {
            if (!configuration.isEmpty()) {
                throw new IllegalArgumentException(
                    "The second parameter can only used when a graph name is given as first parameter");
            }

            Map<String, Object> implicitConfig = (Map<String, Object>) graphNameOrConfig;
            CypherMapWrapper implicitAndAlgoConfig = CypherMapWrapper.create(implicitConfig);

            config = newConfig(Optional.empty(), implicitAndAlgoConfig);

            //TODO: assert that implicitAndAlgoConfig is empty or fail
        } else {
            throw new IllegalArgumentException(
                "The first parameter must be a graph name or a configuration map, but was: " + graphNameOrConfig
            );
        }

        return Pair.of(config, graphName);
    }

    protected Graph loadGraph(Pair<CONFIG, Optional<String>> configAndName) {
        CONFIG config = configAndName.first();
        Optional<String> graphName = configAndName.other();

        if (graphName.isPresent()) {
            return GraphCatalog.getLoadedGraphs(getUsername()).get(graphName.get());
        } else if (config.implicitCreateConfig().isPresent()) {
            GraphCreateConfig createConfig = config.implicitCreateConfig().get();

            GraphLoader loader = new GraphLoader(api, Pools.DEFAULT)
                .init(log, getUsername())
                .withAllocationTracker(AllocationTracker.EMPTY)
                .withTerminationFlag(TerminationFlag.wrap(transaction));
            loader = createConfig.configureLoader(loader);
            return loader.load(createConfig.getGraphImpl());
        } else {
            throw new IllegalStateException("There must be either a graph name or an implicit create config");
        }

    }
}
