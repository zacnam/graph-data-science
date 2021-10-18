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
package org.neo4j.gds.config;

import org.immutables.value.Value;
import org.jetbrains.annotations.Nullable;
import org.neo4j.gds.NodeLabel;
import org.neo4j.gds.RelationshipType;
import org.neo4j.gds.annotation.Configuration;
import org.neo4j.gds.api.GraphStore;
import org.neo4j.gds.core.Username;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;

@SuppressWarnings("immutables:subtype")
public interface BaseConfig {

    String SUDO_KEY = "sudo";

    @Configuration.Parameter
    @Value.Default
    default String username() {
        return Username.EMPTY_USERNAME.username();
    }

    @Value.Default
    @Value.Parameter(false)
    @Configuration.Key("username")
    @Configuration.ConvertWith("org.apache.commons.lang3.StringUtils#trimToNull")
    default @Nullable String usernameOverride() {
        return null;
    }

    @Value.Default
    @Value.Parameter(false)
    @Configuration.Key(SUDO_KEY)
    default boolean sudo() {
        return false;
    }

    @Configuration.CollectKeys
    @Value.Auxiliary
    @Value.Default
    @Value.Parameter(false)
    default Collection<String> configKeys() {
        return Collections.emptyList();
    }

    @Configuration.ToMap
    @Value.Auxiliary
    @Value.Derived
    default Map<String, Object> toMap() {
        return Collections.emptyMap();
    }

    @Configuration.GraphStoreValidation
    @Value.Auxiliary
    @Value.Default
    default void graphStoreValidation(
        GraphStore graphStore,
        Collection<NodeLabel> selectedLabels,
        Collection<RelationshipType> selectedRelationshipTypes
    ) {}
}
