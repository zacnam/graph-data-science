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
package org.neo4j.gds.compat.dev;

import org.neo4j.internal.recordstorage.AbstractInMemoryMetaDataProvider;
import org.neo4j.internal.recordstorage.AbstractTransactionIdStore;
import org.neo4j.storageengine.api.ClosedTransactionMetadata;

public class InMemoryMetaDataProviderImpl extends AbstractInMemoryMetaDataProvider {
    private final InMemoryTransactionIdStoreImpl transactionIdStore;

    public InMemoryMetaDataProviderImpl(InMemoryTransactionIdStoreImpl transactionIdStore) {
        this.transactionIdStore = transactionIdStore;
    }

    @Override
    public ClosedTransactionMetadata getLastClosedTransaction() {
        return this.transactionIdStore.getLastClosedTransaction();
    }

    @Override
    public AbstractTransactionIdStore transactionIdStore() {
        return transactionIdStore;
    }
}
