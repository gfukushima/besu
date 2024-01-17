/*
 * Copyright Hyperledger Besu Contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package org.hyperledger.besu.ethereum.trie.diffbased.bonsai.storage;

import static org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueSegmentIdentifier.ACCOUNT_INFO_STATE;
import static org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueSegmentIdentifier.ACCOUNT_STORAGE_STORAGE;
import static org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueSegmentIdentifier.CODE_STORAGE;
import static org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueSegmentIdentifier.TRIE_BRANCH_STORAGE;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.StorageSlotKey;
import org.hyperledger.besu.ethereum.storage.StorageProvider;
import org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueSegmentIdentifier;
import org.hyperledger.besu.ethereum.trie.MerkleTrie;
import org.hyperledger.besu.ethereum.trie.diffbased.bonsai.storage.flat.FlatDbStrategy;
import org.hyperledger.besu.ethereum.trie.diffbased.bonsai.storage.flat.FullFlatDbStrategy;
import org.hyperledger.besu.ethereum.trie.diffbased.bonsai.storage.flat.PartialFlatDbStrategy;
import org.hyperledger.besu.ethereum.trie.diffbased.common.storage.DiffBasedWorldStateKeyValueStorage;
import org.hyperledger.besu.ethereum.worldstate.DataStorageFormat;
import org.hyperledger.besu.ethereum.worldstate.FlatDbMode;
import org.hyperledger.besu.ethereum.worldstate.StateTrieAccountValue;
import org.hyperledger.besu.ethereum.worldstate.WorldStateKeyValueStorage;
import org.hyperledger.besu.evm.account.AccountStorageEntry;
import org.hyperledger.besu.metrics.ObservableMetricsSystem;
import org.hyperledger.besu.plugin.services.storage.KeyValueStorage;
import org.hyperledger.besu.plugin.services.storage.KeyValueStorageTransaction;
import org.hyperledger.besu.plugin.services.storage.SegmentedKeyValueStorage;
import org.hyperledger.besu.plugin.services.storage.SegmentedKeyValueStorageTransaction;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.function.Supplier;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BonsaiWorldStateKeyValueStorage extends DiffBasedWorldStateKeyValueStorage
    implements WorldStateKeyValueStorage {
  private static final Logger LOG = LoggerFactory.getLogger(BonsaiWorldStateKeyValueStorage.class);
  public static final byte[] FLAT_DB_MODE = "flatDbStatus".getBytes(StandardCharsets.UTF_8);

  protected FlatDbMode flatDbMode;
  protected FlatDbStrategy flatDbStrategy;

  public BonsaiWorldStateKeyValueStorage(
      final StorageProvider provider, final ObservableMetricsSystem metricsSystem) {
    super(
        provider.getStorageBySegmentIdentifiers(
            List.of(
                ACCOUNT_INFO_STATE, CODE_STORAGE, ACCOUNT_STORAGE_STORAGE, TRIE_BRANCH_STORAGE)),
        provider.getStorageBySegmentIdentifier(KeyValueSegmentIdentifier.TRIE_LOG_STORAGE),
        metricsSystem);
    loadFlatDbStrategy();
  }

  public BonsaiWorldStateKeyValueStorage(
      final FlatDbMode flatDbMode,
      final FlatDbStrategy flatDbStrategy,
      final SegmentedKeyValueStorage composedWorldStateStorage,
      final KeyValueStorage trieLogStorage,
      final ObservableMetricsSystem metricsSystem) {
    super(composedWorldStateStorage, trieLogStorage, metricsSystem);
    this.flatDbMode = flatDbMode;
    this.flatDbStrategy = flatDbStrategy;
  }

  private void loadFlatDbStrategy() {
    // derive our flatdb strategy from db or default:
    var newFlatDbMode = deriveFlatDbStrategy();

    // if  flatDbMode is not loaded or has changed, reload flatDbStrategy
    if (this.flatDbMode == null || !this.flatDbMode.equals(newFlatDbMode)) {
      this.flatDbMode = newFlatDbMode;
      if (flatDbMode == FlatDbMode.FULL) {
        this.flatDbStrategy = new FullFlatDbStrategy(metricsSystem);
      } else {
        this.flatDbStrategy = new PartialFlatDbStrategy(metricsSystem);
      }
    }
  }

  public FlatDbMode deriveFlatDbStrategy() {
    var flatDbMode =
        FlatDbMode.fromVersion(
            composedWorldStateStorage
                .get(TRIE_BRANCH_STORAGE, FLAT_DB_MODE)
                .map(Bytes::wrap)
                .orElse(FlatDbMode.PARTIAL.getVersion()));
    LOG.info("Bonsai flat db mode found {}", flatDbMode);

    return flatDbMode;
  }

  @Override
  public FlatDbStrategy getFlatDbStrategy() {
    if (flatDbStrategy == null) {
      loadFlatDbStrategy();
    }
    return flatDbStrategy;
  }

  @Override
  public DataStorageFormat getDataStorageFormat() {
    return DataStorageFormat.BONSAI;
  }

  @Override
  public FlatDbMode getFlatDbMode() {
    return flatDbMode;
  }

  public Optional<Bytes> getCode(final Bytes32 codeHash, final Hash accountHash) {
    if (codeHash.equals(Hash.EMPTY)) {
      return Optional.of(Bytes.EMPTY);
    } else {
      return getFlatDbStrategy().getFlatCode(codeHash, accountHash, composedWorldStateStorage);
    }
  }

  public Optional<Bytes> getAccount(final Hash accountHash) {
    return getFlatDbStrategy()
        .getFlatAccount(
            this::getWorldStateRootHash,
            this::getAccountStateTrieNode,
            accountHash,
            composedWorldStateStorage);
  }

  public Optional<Bytes> getAccountStateTrieNode(final Bytes location, final Bytes32 nodeHash) {
    if (nodeHash.equals(MerkleTrie.EMPTY_TRIE_NODE_HASH)) {
      return Optional.of(MerkleTrie.EMPTY_TRIE_NODE);
    } else {
      return composedWorldStateStorage
          .get(TRIE_BRANCH_STORAGE, location.toArrayUnsafe())
          .map(Bytes::wrap)
          .filter(b -> Hash.hash(b).equals(nodeHash));
    }
  }

  public Optional<Bytes> getAccountStorageTrieNode(
      final Hash accountHash, final Bytes location, final Bytes32 nodeHash) {
    if (nodeHash.equals(MerkleTrie.EMPTY_TRIE_NODE_HASH)) {
      return Optional.of(MerkleTrie.EMPTY_TRIE_NODE);
    } else {
      return composedWorldStateStorage
          .get(TRIE_BRANCH_STORAGE, Bytes.concatenate(accountHash, location).toArrayUnsafe())
          .map(Bytes::wrap)
          .filter(b -> Hash.hash(b).equals(nodeHash));
    }
  }

  public Optional<Bytes> getTrieNodeUnsafe(final Bytes key) {
    return composedWorldStateStorage
        .get(TRIE_BRANCH_STORAGE, Bytes.concatenate(key).toArrayUnsafe())
        .map(Bytes::wrap);
  }

  public Optional<Bytes> getStorageValueByStorageSlotKey(
      final Hash accountHash, final StorageSlotKey storageSlotKey) {
    return getStorageValueByStorageSlotKey(
        () ->
            getAccount(accountHash)
                .map(
                    b ->
                        StateTrieAccountValue.readFrom(
                                org.hyperledger.besu.ethereum.rlp.RLP.input(b))
                            .getStorageRoot()),
        accountHash,
        storageSlotKey);
  }

  public Optional<Bytes> getStorageValueByStorageSlotKey(
      final Supplier<Optional<Hash>> storageRootSupplier,
      final Hash accountHash,
      final StorageSlotKey storageSlotKey) {
    return getFlatDbStrategy()
        .getFlatStorageValueByStorageSlotKey(
            this::getWorldStateRootHash,
            storageRootSupplier,
            (location, hash) -> getAccountStorageTrieNode(accountHash, location, hash),
            accountHash,
            storageSlotKey,
            composedWorldStateStorage);
  }

  public NavigableMap<Bytes32, AccountStorageEntry> storageEntriesFrom(
      final Hash addressHash, final Bytes32 startKeyHash, final int limit) {
    throw new RuntimeException("Bonsai Tries does not currently support enumerating storage");
  }

  public void upgradeToFullFlatDbMode() {
    final SegmentedKeyValueStorageTransaction transaction =
        composedWorldStateStorage.startTransaction();
    // TODO: consider ARCHIVE mode
    transaction.put(
        TRIE_BRANCH_STORAGE, FLAT_DB_MODE, FlatDbMode.FULL.getVersion().toArrayUnsafe());
    transaction.commit();
    loadFlatDbStrategy(); // force reload of flat db reader strategy
  }

  public void downgradeToPartialFlatDbMode() {
    final SegmentedKeyValueStorageTransaction transaction =
        composedWorldStateStorage.startTransaction();
    transaction.put(
        TRIE_BRANCH_STORAGE, FLAT_DB_MODE, FlatDbMode.PARTIAL.getVersion().toArrayUnsafe());
    transaction.commit();
    loadFlatDbStrategy(); // force reload of flat db reader strategy
  }

  @Override
  public void clear() {
    super.clear();
    loadFlatDbStrategy(); // force reload of flat db reader strategy
  }

  @Override
  public Updater updater() {
    return new Updater(
        composedWorldStateStorage.startTransaction(),
        trieLogStorage.startTransaction(),
        flatDbStrategy);
  }

  public static class Updater implements DiffBasedWorldStateKeyValueStorage.Updater {

    private final SegmentedKeyValueStorageTransaction composedWorldStateTransaction;
    private final KeyValueStorageTransaction trieLogStorageTransaction;
    private final FlatDbStrategy flatDbStrategy;

    public Updater(
        final SegmentedKeyValueStorageTransaction composedWorldStateTransaction,
        final KeyValueStorageTransaction trieLogStorageTransaction,
        final FlatDbStrategy flatDbStrategy) {

      this.composedWorldStateTransaction = composedWorldStateTransaction;
      this.trieLogStorageTransaction = trieLogStorageTransaction;
      this.flatDbStrategy = flatDbStrategy;
    }

    public Updater removeCode(final Hash accountHash) {
      flatDbStrategy.removeFlatCode(composedWorldStateTransaction, accountHash);
      return this;
    }

    public Updater putCode(final Hash accountHash, final Bytes code) {
      // Skip the hash calculation for empty code
      final Hash codeHash = code.size() == 0 ? Hash.EMPTY : Hash.hash(code);
      return putCode(accountHash, codeHash, code);
    }

    public Updater putCode(final Hash accountHash, final Bytes32 codeHash, final Bytes code) {
      if (code.size() == 0) {
        // Don't save empty values
        return this;
      }
      flatDbStrategy.putFlatCode(composedWorldStateTransaction, accountHash, codeHash, code);
      return this;
    }

    public Updater removeAccountInfoState(final Hash accountHash) {
      flatDbStrategy.removeFlatAccount(composedWorldStateTransaction, accountHash);
      return this;
    }

    public Updater putAccountInfoState(final Hash accountHash, final Bytes accountValue) {
      if (accountValue.size() == 0) {
        // Don't save empty values
        return this;
      }
      flatDbStrategy.putFlatAccount(composedWorldStateTransaction, accountHash, accountValue);
      return this;
    }

    @Override
    public Updater saveWorldState(final Bytes blockHash, final Bytes32 nodeHash, final Bytes node) {
      composedWorldStateTransaction.put(
          TRIE_BRANCH_STORAGE, Bytes.EMPTY.toArrayUnsafe(), node.toArrayUnsafe());
      composedWorldStateTransaction.put(
          TRIE_BRANCH_STORAGE, WORLD_ROOT_HASH_KEY, nodeHash.toArrayUnsafe());
      composedWorldStateTransaction.put(
          TRIE_BRANCH_STORAGE, WORLD_BLOCK_HASH_KEY, blockHash.toArrayUnsafe());
      return this;
    }

    public Updater putAccountStateTrieNode(
        final Bytes location, final Bytes32 nodeHash, final Bytes node) {
      if (nodeHash.equals(MerkleTrie.EMPTY_TRIE_NODE_HASH)) {
        // Don't save empty nodes
        return this;
      }
      composedWorldStateTransaction.put(
          TRIE_BRANCH_STORAGE, location.toArrayUnsafe(), node.toArrayUnsafe());
      return this;
    }

    public Updater removeAccountStateTrieNode(final Bytes location) {
      composedWorldStateTransaction.remove(TRIE_BRANCH_STORAGE, location.toArrayUnsafe());
      return this;
    }

    public synchronized Updater putAccountStorageTrieNode(
        final Hash accountHash, final Bytes location, final Bytes32 nodeHash, final Bytes node) {
      if (nodeHash.equals(MerkleTrie.EMPTY_TRIE_NODE_HASH)) {
        // Don't save empty nodes
        return this;
      }
      composedWorldStateTransaction.put(
          TRIE_BRANCH_STORAGE,
          Bytes.concatenate(accountHash, location).toArrayUnsafe(),
          node.toArrayUnsafe());
      return this;
    }

    public synchronized Updater putStorageValueBySlotHash(
        final Hash accountHash, final Hash slotHash, final Bytes storage) {
      flatDbStrategy.putFlatAccountStorageValueByStorageSlotHash(
          composedWorldStateTransaction, accountHash, slotHash, storage);
      return this;
    }

    public synchronized void removeStorageValueBySlotHash(
        final Hash accountHash, final Hash slotHash) {
      flatDbStrategy.removeFlatAccountStorageValueByStorageSlotHash(
          composedWorldStateTransaction, accountHash, slotHash);
    }

    @Override
    public SegmentedKeyValueStorageTransaction getWorldStateTransaction() {
      return composedWorldStateTransaction;
    }

    @Override
    public KeyValueStorageTransaction getTrieLogStorageTransaction() {
      return trieLogStorageTransaction;
    }

    @Override
    public void commit() {
      // write the log ahead, then the worldstate
      trieLogStorageTransaction.commit();
      composedWorldStateTransaction.commit();
    }

    @Override
    public void rollback() {
      composedWorldStateTransaction.rollback();
      trieLogStorageTransaction.rollback();
    }
  }
}
