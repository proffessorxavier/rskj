/*
 * This file is part of RskJ
 * Copyright (C) 2018 RSK Labs Ltd.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package co.rsk.db;

import co.rsk.config.RskSystemProperties;
import co.rsk.core.Rsk;
import co.rsk.core.RskAddress;
import co.rsk.trie.TrieCopier;
import co.rsk.trie.TrieImpl;
import co.rsk.trie.TrieStore;
import co.rsk.trie.TrieStoreImpl;
import org.ethereum.core.Blockchain;
import org.ethereum.datasource.KeyValueDataSource;
import org.ethereum.util.FileUtil;

import static org.ethereum.datasource.DataSourcePool.levelDbByName;

/**
 * Created by ajlopez on 21/03/2018.
 */
public class PruneService {
    private static final int noBlocks = 100;
    private static final int forkBlocks = 30;

    private final TrieCopier trieCopier = new TrieCopier();
    private final RskSystemProperties config;
    private final Blockchain blockchain;
    private final RskAddress contractAddress;

    public PruneService(RskSystemProperties config, Blockchain blockchain, RskAddress contractAddress) {
        this.config = config;
        this.blockchain = blockchain;
        this.contractAddress = contractAddress;
    }

    public void process() {
        long from = this.blockchain.getBestBlock().getNumber() - noBlocks;
        String dataSourceName = getDataSourceName(contractAddress);
        KeyValueDataSource sourceDataSource = levelDbByName(this.config, dataSourceName);
        TrieStore sourceStore = new TrieStoreImpl(sourceDataSource);
        KeyValueDataSource targetDataSource = levelDbByName(this.config, dataSourceName + "B");
        TrieStore targetStore = new TrieStoreImpl(targetDataSource);

        trieCopier.trieContractStateCopy(sourceStore, targetStore, blockchain, from, 0, blockchain.getRepository(), this.contractAddress);

        targetDataSource.close();
        sourceDataSource.close();

        String contractDirectoryName = getDatabaseDirectory(config, dataSourceName);

        removeDirectory(contractDirectoryName);
        boolean result = FileUtil.fileRename(contractDirectoryName + "B", contractDirectoryName);
    }

    private static String getDatabaseDirectory(RskSystemProperties config, String subdirectoryName) {
        return FileUtil.getDatabaseDirectoryPath(config.databaseDir(), subdirectoryName).toString();
    }

    private static String getDataSourceName(RskAddress contractAddress) {
        return "details-storage/" + contractAddress;
    }

    private static void removeDirectory(String directoryName) {
        FileUtil.recursiveDelete(directoryName);
    }
}
