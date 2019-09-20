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
package org.neo4j.graphalgo.core.loading;

import org.junit.jupiter.api.Test;
import org.neo4j.internal.kernel.api.RelationshipTraversalCursor;
import org.neo4j.internal.kernel.api.helpers.StubCursorFactory;
import org.neo4j.internal.kernel.api.helpers.StubNodeCursor;
import org.neo4j.internal.kernel.api.helpers.StubRelationshipCursor;
import org.neo4j.internal.kernel.api.helpers.TestRelationshipChain;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LoadRelationshipsTest {

    @Test
    void allRelationshipsUnique() {
        StubNodeCursor stubNodeCursor = new StubNodeCursor(false);

        StubCursorFactory stubCursorFactory = new StubCursorFactory();

        TestRelationshipChain chain = new TestRelationshipChain(0)
                .outgoing(0, 3, 0)
                .outgoing(1, 4, 0);

        StubRelationshipCursor stubRelationshipCursor = new StubRelationshipCursor(chain);
        stubCursorFactory.withRelationshipTraversalCursors(stubRelationshipCursor);

        LoadAllRelationships loadAllRelationships = new LoadAllRelationships(stubCursorFactory);

        int degreeBoth = loadAllRelationships.degreeBoth(stubNodeCursor);
        assertEquals(2, degreeBoth);
    }

    @Test
    void countMultipleRelationshipsBetweenNodesOnce() {
        StubNodeCursor stubNodeCursor = new StubNodeCursor(false);

        StubCursorFactory stubCursorFactory = new StubCursorFactory();

        TestRelationshipChain node0Chain = new TestRelationshipChain(0)
                .outgoing(0, 1, 0)
                .incoming(1, 1, 0);
        RelationshipTraversalCursor node0Cursor = new StubRelationshipCursor(node0Chain);

        stubCursorFactory.withRelationshipTraversalCursors(node0Cursor);

        LoadAllRelationships loadAllRelationships = new LoadAllRelationships(stubCursorFactory);

        int degreeBoth = loadAllRelationships.degreeBoth(stubNodeCursor);
        assertEquals(1, degreeBoth);
    }

    @Test
    void countMultipleRelationshipsBetweenNodesOnceType() {
        StubNodeCursor stubNodeCursor = new StubNodeCursor(false);

        StubCursorFactory stubCursorFactory = new StubCursorFactory();

        TestRelationshipChain node0Chain = new TestRelationshipChain(0)
                .outgoing(0, 1, 1)
                .outgoing(1, 2, 2)
                .incoming(2, 1, 1);
        RelationshipTraversalCursor node0Cursor = new StubRelationshipCursor(node0Chain);

        stubCursorFactory.withRelationshipTraversalCursors(node0Cursor);

        LoadRelationshipsOfSingleType loadAllRelationships = new LoadRelationshipsOfSingleType(stubCursorFactory, new int[] {1});

        int degreeBoth = loadAllRelationships.degreeBoth(stubNodeCursor);
        assertEquals(1, degreeBoth);
    }


}
