/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
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

package co.rsk.trie;

import co.rsk.crypto.Keccak256;
import org.ethereum.datasource.HashMapDB;
import org.junit.Assert;
import org.junit.Test;

import java.util.Random;

/**
 * Created by ajlopez on 09/03/2018.
 */
public class TrieCopierTest {
    private static Random random = new Random();

    @Test
    public void copyTrie() {
        HashMapDB map1 = new HashMapDB();
        TrieStoreImpl store1 = new TrieStoreImpl(map1);
        HashMapDB map2 = new HashMapDB();
        TrieStoreImpl store2 = new TrieStoreImpl(map2);

        int nvalues = 10;
        byte[][] values = createValues(nvalues, 100);

        Trie trie = new TrieImpl(store1, true);

        for (int k = 0; k < nvalues; k++)
            trie = trie.put(k + "", values[k]);

        trie.save();

        TrieCopier.trieCopy(store1, store2, trie.getHash());

        Trie result = store2.retrieve(trie.getHash().getBytes());

        Assert.assertNotNull(result);
        Assert.assertEquals(trie.getHash(), result.getHash());

        for (int k = 0; k < nvalues; k++)
            Assert.assertArrayEquals(trie.get(k + ""), result.get(k + ""));
    }

    @Test
    public void copyThreeTries() {
        HashMapDB map1 = new HashMapDB();
        TrieStoreImpl store1 = new TrieStoreImpl(map1);
        HashMapDB map2 = new HashMapDB();
        TrieStoreImpl store2 = new TrieStoreImpl(map2);

        int nvalues = 30;
        byte[][] values = createValues(nvalues, 100);

        Trie trie = new TrieImpl(store1, true);

        for (int k = 0; k < nvalues - 2; k++)
            trie = trie.put(k + "", values[k]);

        trie.save();
        Keccak256 hash1 = trie.getHash();

        trie.put((nvalues - 2) + "", values[nvalues - 2]);
        trie.save();
        Keccak256 hash2 = trie.getHash();

        trie.put((nvalues - 1) + "", values[nvalues - 1]);
        trie.save();
        Keccak256 hash3 = trie.getHash();

        TrieCopier.trieCopy(store1, store2, hash1);
        TrieCopier.trieCopy(store1, store2, hash2);
        TrieCopier.trieCopy(store1, store2, hash3);

        Trie result1 = store2.retrieve(hash1.getBytes());

        Assert.assertNotNull(result1);
        Assert.assertEquals(hash1, result1.getHash());

        for (int k = 0; k < nvalues - 2; k++)
            Assert.assertArrayEquals(trie.get(k + ""), result1.get(k + ""));

        Trie result2 = store2.retrieve(hash2.getBytes());

        Assert.assertNotNull(result2);
        Assert.assertEquals(hash2, result2.getHash());
        Assert.assertNull(result1.get((nvalues - 2) + ""));
        Assert.assertArrayEquals(trie.get((nvalues - 2) + ""), result2.get((nvalues - 2) + ""));

        Trie result3 = store2.retrieve(hash3.getBytes());

        Assert.assertNotNull(result3);
        Assert.assertEquals(hash3, result3.getHash());
        Assert.assertNull(result1.get((nvalues - 1) + ""));
        Assert.assertNull(result2.get((nvalues - 1) + ""));
        Assert.assertArrayEquals(trie.get((nvalues - 1) + ""), result3.get((nvalues - 1) + ""));
    }

    private byte[][] createValues(int nvalues, int length) {
        byte[][] values = new byte[nvalues][];

        for (int k = 0; k < nvalues; k++) {
            byte[] value = new byte[length];
            random.nextBytes(value);
            values[k] = value;
        }

        return values;
    }
}
