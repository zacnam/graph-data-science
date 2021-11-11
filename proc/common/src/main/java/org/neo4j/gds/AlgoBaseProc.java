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
package org.neo4j.gds;

import org.eclipse.collections.api.tuple.Pair;
import org.eclipse.collections.impl.tuple.Tuples;
import org.immutables.value.Value;
import org.jetbrains.annotations.Nullable;
import org.neo4j.gds.annotation.ValueClass;
import org.neo4j.gds.api.Graph;
import org.neo4j.gds.api.GraphLoaderContext;
import org.neo4j.gds.api.GraphStore;
import org.neo4j.gds.api.ImmutableGraphLoaderContext;
import org.neo4j.gds.api.NodeProperties;
import org.neo4j.gds.config.AlgoBaseConfig;
import org.neo4j.gds.config.GraphCreateConfig;
import org.neo4j.gds.config.RelationshipWeightConfig;
import org.neo4j.gds.core.CypherMapWrapper;
import org.neo4j.gds.core.GraphDimensions;
import org.neo4j.gds.core.utils.ProgressTimer;
import org.neo4j.gds.core.utils.TerminationFlag;
import org.neo4j.gds.core.utils.mem.AllocationTracker;
import org.neo4j.gds.core.utils.mem.MemoryEstimations;
import org.neo4j.gds.core.utils.mem.MemoryTree;
import org.neo4j.gds.core.utils.mem.MemoryTreeWithDimensions;
import org.neo4j.gds.results.MemoryEstimateResult;
import org.neo4j.gds.transaction.TransactionContext;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static org.neo4j.gds.config.BaseConfig.SUDO_KEY;
import static org.neo4j.gds.config.ConcurrencyConfig.CONCURRENCY_KEY;
import static org.neo4j.gds.config.ConcurrencyConfig.DEFAULT_CONCURRENCY;
import static org.neo4j.gds.config.GraphCreateConfig.READ_CONCURRENCY_KEY;

public abstract class AlgoBaseProc<
    ALGO extends Algorithm<ALGO, ALGO_RESULT>,
    ALGO_RESULT,
    CONFIG extends AlgoBaseConfig> extends BaseProc {

    protected static final String STATS_DESCRIPTION = "Executes the algorithm and returns result statistics without writing the result to Neo4j.";

    protected String procName() {
        return this.getClass().getSimpleName();
    }

    public CONFIG newConfig(Optional<String> graphName, CypherMapWrapper config) {
        Optional<GraphCreateConfig> maybeImplicitCreate = Optional.empty();
        Collection<String> allowedKeys = new HashSet<>();
        // implicit loading
        if (graphName.isEmpty()) {
            // inherit concurrency from AlgoBaseConfig
            if (!config.containsKey(READ_CONCURRENCY_KEY)) {
                config = config.withNumber(READ_CONCURRENCY_KEY, config.getInt(CONCURRENCY_KEY, DEFAULT_CONCURRENCY));
            }
            GraphCreateConfig createConfig = GraphCreateConfig.createImplicit(username(), config);
            maybeImplicitCreate = Optional.of(createConfig);
            allowedKeys.addAll(createConfig.configKeys());
            CypherMapWrapper configWithoutCreateKeys = config.withoutAny(allowedKeys);
            // check if we have an explicit configured sudo key, as this one is
            // shared between create and algo configs
            for (var entry : allSharedConfigKeys().entrySet()) {
                var value = config.getChecked(entry.getKey(), null, entry.getValue());
                if (value != null) {
                    configWithoutCreateKeys = configWithoutCreateKeys.withEntry(entry.getKey(), value);
                }
            }
            config = configWithoutCreateKeys;
        }
        CONFIG algoConfig = newConfig(username(), graphName, maybeImplicitCreate, config);
        setAlgorithmMetaDataToTransaction(algoConfig);
        allowedKeys.addAll(algoConfig.configKeys());
        validateConfig(config, allowedKeys);
        return algoConfig;
    }

    private void setAlgorithmMetaDataToTransaction(CONFIG algoConfig) {
        if (transaction == null) {
            return;
        }
        var metaData = transaction.getMetaData();
        if (metaData instanceof AlgorithmMetaData) {
            ((AlgorithmMetaData) metaData).set(algoConfig);
        }
    }

    private Map<String, Class<?>> allSharedConfigKeys() {
        var configKeys = new HashMap<String, Class<?>>(sharedConfigKeys());
        configKeys.put(SUDO_KEY, Boolean.class);
        return configKeys;
    }

    /**
     * If the algorithm config shares any configuration parameters with anonymous projections, these must be declared here.
     */
    protected Map<String, Class<?>> sharedConfigKeys() {
        return Map.of();
    }

    protected abstract CONFIG newConfig(
        String username,
        Optional<String> graphName,
        Optional<GraphCreateConfig> maybeImplicitCreate,
        CypherMapWrapper config
    );

    protected abstract AlgorithmFactory<ALGO, CONFIG> algorithmFactory();

    public Pair<CONFIG, Optional<String>> processInput(Object graphNameOrConfig, Map<String, Object> configuration) {
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

        return Tuples.pair(config, graphName);
    }

    protected void validateConfigsBeforeLoad(
        GraphCreateConfig graphCreateConfig,
        CONFIG config
    ) {}

    public final void validateConfigWithGraphStore(
        GraphStore graphStore,
        GraphCreateConfig graphCreateConfig,
        CONFIG config
    ) {
        config.graphStoreValidation(
            graphStore,
            config.nodeLabelIdentifiers(graphStore),
            config.internalRelationshipTypes(graphStore)
        );
        GraphStoreValidation.validate(graphStore, config);
        validateConfigsAfterLoad(graphStore, graphCreateConfig, config);
    }

    protected void validateConfigsAfterLoad(
        GraphStore graphStore,
        GraphCreateConfig graphCreateConfig,
        CONFIG config
    ) {}

    protected ComputationResult<ALGO, ALGO_RESULT, CONFIG> compute(
        Object graphNameOrConfig,
        Map<String, Object> configuration
    ) {
        ProcPreconditions.check();
        return compute(graphNameOrConfig, configuration, true, true);
    }

    protected ComputationResult<ALGO, ALGO_RESULT, CONFIG> compute(
        Object graphNameOrConfig,
        Map<String, Object> configuration,
        boolean releaseAlgorithm,
        boolean releaseTopology
    ) {
        ImmutableComputationResult.Builder<ALGO, ALGO_RESULT, CONFIG> builder = ImmutableComputationResult.builder();
        var allocationTracker = allocationTracker();

        Pair<CONFIG, Optional<String>> input = processInput(graphNameOrConfig, configuration);
        CONFIG config = input.getOne();

        var memoryEstimationInBytes = tryValidateMemoryUsage(config, this::memoryEstimation);

        GraphStore graphStore;
        Graph graph;

        try (ProgressTimer timer = ProgressTimer.start(builder::createMillis)) {
            graphStore = getOrCreateGraphStore(input);
            graph = createGraph(graphStore, config);
        }

        if (graph.isEmpty()) {
            return builder
                .isGraphEmpty(true)
                .graph(graph)
                .graphStore(graphStore)
                .config(config)
                .computeMillis(0)
                .result(null)
                .algorithm(null)
                .build();
        }

        ALGO algo = newAlgorithm(graph, config, allocationTracker);

        algo.progressTracker.setEstimatedResourceFootprint(memoryEstimationInBytes, config.concurrency());

        ALGO_RESULT result = runWithExceptionLogging(
            "Computation failed",
            () -> {
                try (ProgressTimer ignored = ProgressTimer.start(builder::computeMillis)) {
                    return algo.compute();
                } catch (Exception e) {
                    algo.progressTracker.fail();
                    throw e;
                } finally {
                    if (releaseAlgorithm) {
                        algo.progressTracker.release();
                        algo.release();
                    }
                    if (releaseTopology) {
                        graph.releaseTopology();
                    }
                }
            }
        );

        log.info(procName() + ": overall memory usage %s", allocationTracker.getUsageString());

        return builder
            .graph(graph)
            .graphStore(graphStore)
            .algorithm(algo)
            .result(result)
            .config(config)
            .build();
    }

    /**
     * Returns a single node property that has been produced by the procedure.
     */
    protected NodeProperties nodeProperties(ComputationResult<ALGO, ALGO_RESULT, CONFIG> computationResult) {
        throw new UnsupportedOperationException("Procedure must implement org.neo4j.gds.AlgoBaseProc.nodeProperty");
    }

    protected Stream<MemoryEstimateResult> computeEstimate(
        Object graphNameOrConfig,
        Map<String, Object> configuration
    ) {
        Pair<CONFIG, Optional<String>> configAndGraphName = processInput(
            graphNameOrConfig,
            configuration
        );

        MemoryTreeWithDimensions memoryTreeWithDimensions = memoryEstimation(configAndGraphName.getOne());
        return Stream.of(
            new MemoryEstimateResult(memoryTreeWithDimensions)
        );
    }

    MemoryTreeWithDimensions memoryEstimation(CONFIG config) {
        MemoryEstimations.Builder estimationBuilder = MemoryEstimations.builder("Memory Estimation");

        GraphStoreLoader graphStoreLoader;
        // We need to manually decide which store loader to choose as
        // opposed to using `GraphStoreLoader.of()` as i.e. the estimation CLI
        // will set only a minimal amount of fields in the base proc.
        // Calling into username() would cause a null pointer exception.
        // However, we know that the CLI will always use the implicit loader.
        if (config.implicitCreateConfig().isPresent()) {
            graphStoreLoader = ImplicitGraphStoreLoader.fromBaseProc(
                config.implicitCreateConfig().get(),
                taskRegistryFactory,
                this
            );
        } else {
            String graphName = config.graphName().orElseThrow(IllegalStateException::new);
            graphStoreLoader = new GraphStoreFromCatalogLoader(
                graphName,
                config,
                username(),
                databaseId(),
                isGdsAdmin()
            );
        }

        GraphDimensions estimateDimensions = graphStoreLoader.memoryEstimation(config, estimationBuilder);

        estimationBuilder.add("algorithm", algorithmFactory().memoryEstimation(config));

        MemoryTree memoryTree = estimationBuilder.build().estimate(estimateDimensions, config.concurrency());
        return new MemoryTreeWithDimensions(memoryTree, estimateDimensions);
    }

    private ALGO newAlgorithm(
        final Graph graph,
        final CONFIG config,
        final AllocationTracker allocationTracker
    ) {
        TerminationFlag terminationFlag = TerminationFlag.wrap(transaction);
        return algorithmFactory()
            .build(graph, config, allocationTracker, log, taskRegistryFactory)
            .withTerminationFlag(terminationFlag);
    }

    private Graph createGraph(GraphStore graphStore, CONFIG config) {
        Optional<String> weightProperty = config instanceof RelationshipWeightConfig
            ? Optional.ofNullable(((RelationshipWeightConfig) config).relationshipWeightProperty())
            : Optional.empty();

        Collection<NodeLabel> nodeLabels = config.nodeLabelIdentifiers(graphStore);
        Collection<RelationshipType> relationshipTypes = config.internalRelationshipTypes(graphStore);

        return graphStore.getGraph(nodeLabels, relationshipTypes, weightProperty);
    }

    protected GraphStore getOrCreateGraphStore(Pair<CONFIG, Optional<String>> configAndName) {
        CONFIG config = configAndName.getOne();
        Optional<String> maybeGraphName = configAndName.getTwo();

        var graphLoaderContext = graphLoaderContext();

        var graphStoreLoader = GraphStoreLoader.of(
            config,
            maybeGraphName,
            username(),
            databaseId(),
            isGdsAdmin(),
            graphLoaderContext
        );

        var graphCreateConfig = graphStoreLoader.graphCreateConfig();
        validateConfigsBeforeLoad(graphCreateConfig, config);
        var graphStore = graphStoreLoader.graphStore();
        validateConfigWithGraphStore(graphStore, graphCreateConfig, config);

        return graphStore;
    }

    private GraphLoaderContext graphLoaderContext() {
        return ImmutableGraphLoaderContext.builder()
            .transactionContext(TransactionContext.of(api, procedureTransaction))
            .api(api)
            .log(log)
            .allocationTracker(allocationTracker)
            .taskRegistryFactory(taskRegistryFactory)
            .terminationFlag(TerminationFlag.wrap(transaction))
            .build();
    }

    @ValueClass
    public interface ComputationResult<A extends Algorithm<A, RESULT>, RESULT, CONFIG extends AlgoBaseConfig> {
        long createMillis();

        long computeMillis();

        @Nullable
        A algorithm();

        @Nullable
        RESULT result();

        Graph graph();

        GraphStore graphStore();

        CONFIG config();

        @Value.Default
        default boolean isGraphEmpty() {
            return false;
        }
    }
}
